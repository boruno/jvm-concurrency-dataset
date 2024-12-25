@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis", "UNCHECKED_CAST")

//package day2

import day2.MSQueueWithConstantTimeRemove.Node.Status.FAILED
import day2.MSQueueWithConstantTimeRemove.Node.Status.SUCCESS
import day2.MSQueueWithConstantTimeRemove.Node.Status.UNDECIDED
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val node = Node(element, curTail)
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                if (curTail.extractedOrRemoved) {
                    curTail.remove()
                }
                return
            } else {
                tail.compareAndSet(curTail, curTail.get(curTail.next))
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHead = head.get()
            val currHeadNext = currHead.get(currHead.next) ?: return null
            currHeadNext.prev.set(null)
            if (head.compareAndSet(currHead, currHeadNext) && currHeadNext.markExtractedOrRemoved()) {
                val element = currHeadNext.element
                currHeadNext.element = null
                return element
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.get(node.next) ?: return false
            if (next.element == element && next.remove()) return true
            node = next
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(head.get().prev.get() == null) {
            "`head.prev` must be null"
        }
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.get()
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.get(node.next)
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.get()
            check(nodeNextPrev != null) {
                "The `prev` pointer of node with element ${nodeNext.element} is `null`, while the node is in the middle of the queue"
            }
            check(nodeNextPrev == node) {
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}"
            }
            // Process the next node.
            node = nodeNext
        }
    }

    private class Node<E>(
        var element: E?,
        prev: Node<E>?
    ) {
        val next: AtomicReference<Any?> = AtomicReference(null)
        val prev: AtomicReference<Any?> = AtomicReference(prev)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved
            get() =
                _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean =
            _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            val removed = markExtractedOrRemoved()
            while (true) {
                val curNext = next.get()
                val curPrev = prev.get()
                // head or tail
                if (curPrev != null && curNext != null) {
                    if (curPrev !is Node<*> || curNext !is Node<*>) {
                        continue
                    }
                    if (!cas2(curPrev.next, this, curNext as Node<E>, curNext.prev, this, curPrev as Node<E>)) {
                        continue
                    }
                    if (curNext.extractedOrRemoved) {
                        curNext.remove()
                    }
                }
                return removed
            }
        }

        fun get(index: AtomicReference<Any?>): Node<E>? = when (val element = index.get()) {
            is Node<*>.CAS2Descriptor -> {
                if (element.status.get() === SUCCESS) {
                    if (element.index1 == index) {
                        element.update1 as Node<E>?
                    } else {
                        element.update2 as Node<E>?
                    }
                } else {
                    if (element.index1 == index) {
                        element.expected1 as Node<E>?
                    } else {
                        element.expected2 as Node<E>?
                    }
                }
            }

            is Node<*>.DCSSDescriptor -> {
                if (element.statusReference.get() === element.expectedCellState) {
                    element.updateCellState as Node<E>?
                } else {
                    element.expectedCellState as Node<E>?
                }
            }

            else -> element as Node<E>?
        }

        fun cas2(
            index1: AtomicReference<Any?>, expected1: Node<E>, update1: Node<E>,
            index2: AtomicReference<Any?>, expected2: Node<E>, update2: Node<E>
        ): Boolean {
            require(index1 != index2) { "The indices should be different" }
            val descriptor = CAS2Descriptor(
                index1 = index1, expected1 = expected1, update1 = update1,
                index2 = index2, expected2 = expected2, update2 = update2
            )
            descriptor.apply()
            return descriptor.status.get() === SUCCESS
        }

        inner class CAS2Descriptor(
            val index1: AtomicReference<Any?>,
            val expected1: Node<E>,
            val update1: Node<E>,
            val index2: AtomicReference<Any?>,
            val expected2: Node<E>,
            val update2: Node<E>
        ) {
            val status = AtomicReference(UNDECIDED)

            fun apply() {
                val result = install()
                updateStatus(result)
                updateCells()
            }

            private fun install() = install(index1, expected1) && install(index2, expected2)

            private fun install(index: AtomicReference<Any?>, expected: Node<E>): Boolean {
                while (true) {
                    val curStatus = status.get()
                    if (curStatus !== UNDECIDED) {
                        return curStatus == SUCCESS
                    }
                    when (val element = index.get()) {
                        this -> return true
                        expected -> if (DCSSDescriptor(index, expected, this, status, UNDECIDED).dcss()) return true
                        is Node<*>.CAS2Descriptor -> element.apply()
                        is Node<*>.DCSSDescriptor -> element.dcss()
                        else -> return false
                    }
                }
            }

            private fun updateStatus(result: Boolean) {
                status.compareAndSet(UNDECIDED, if (result) SUCCESS else FAILED)
            }

            private fun updateCells() {
                val result = status.get() == SUCCESS
                index1.compareAndSet(this, if (result) update1 else expected1)
                index2.compareAndSet(this, if (result) update2 else expected2)
            }
        }

        enum class Status {
            UNDECIDED, SUCCESS, FAILED
        }

        inner class DCSSDescriptor(
            val index: AtomicReference<Any?>,
            val expectedCellState: Any?,
            val updateCellState: Any?,
            val statusReference: AtomicReference<*>,
            val expectedStatus: Any?
        ) {
            fun dcss(): Boolean {
                while (true) {
                    when (val element = index.get()) {
                        expectedCellState -> {
                            if (statusReference.get() != expectedStatus
                                || !index.compareAndSet(expectedCellState, this)
                            ) {
                                return false
                            }
                        }

                        this -> {
                            if (statusReference.get() != expectedStatus) {
                                index.compareAndSet(element, expectedCellState)
                                return false
                            }
                            return index.compareAndSet(element, updateCellState)
                        }

                        is Node<*>.DCSSDescriptor -> {
                            if (element.statusReference.get() != element.expectedStatus) {
                                index.compareAndSet(element, element.expectedCellState)
                                continue
                            }
                            index.compareAndSet(element, element.updateCellState)
                        }

                        else -> return false
                    }
                }
            }
        }
    }
}