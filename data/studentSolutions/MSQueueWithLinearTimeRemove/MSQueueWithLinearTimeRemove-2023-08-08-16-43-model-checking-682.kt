@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day2

import java.util.concurrent.atomic.*

class MSQueueWithLinearTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val dummy = Node(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        val curTail = tail.get()
        val node = Node(element)
        if (curTail.next.compareAndSet(null, node)) {
            tail.compareAndSet(curTail, node)
        } else {
            tail.compareAndSet(curTail, curTail.next.get())
            return enqueue(element)
        }
        if (curTail.extractedOrRemoved) {
            curTail.removePhysically()
        }
    }

    override fun dequeue(): E? {
        val curHead = head.get()
        val nextHead = curHead.next.get() ?: return null
        return if (head.compareAndSet(
                curHead,
                nextHead
            ) && nextHead.markExtractedOrRemoved()
        ) nextHead.element else dequeue()
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get()
            if (next == null) return false
            node = next
            if (node.element == element && node.remove()) return true
        }
    }

    /**
     * This is an internal function for tests.
     * DO NOT CHANGE THIS CODE.
     */
    override fun validate() {
        check(tail.get().next.get() == null) {
            "tail.next must be null"
        }
        var node = head.get()
        // Traverse the linked list
        while (true) {
            if (node !== head.get() && node !== tail.get()) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of this queue"
                }
            }
            node = node.next.get() ?: break
        }
    }

    private inner class Node(
        var element: E?
    ) {
        val next = AtomicReference<Node?>(null)

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
            return if (markExtractedOrRemoved()) {
                if (next.get() != null) {
                    removePhysically()
                }
                true
            } else {
                false
            }
        }

        fun findPreviousNode(): Node? {
            var previous: Node? = null
            var next: Node = head.get()
            while (next != this) {
                previous = next
                next = next.next.get() ?: return null
            }
            return previous
        }
    }

    private fun Node.removePhysically() {
        val previous = findPreviousNode()
        val nextValue = next.get()
        previous?.next?.set(nextValue)
        if (nextValue?.extractedOrRemoved == true) {
            nextValue.removePhysically()
        }
    }
}
