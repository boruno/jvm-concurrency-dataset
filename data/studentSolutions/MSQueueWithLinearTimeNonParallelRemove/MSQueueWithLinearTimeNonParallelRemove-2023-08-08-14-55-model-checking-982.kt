@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day2

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MSQueueWithLinearTimeNonParallelRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val dummy = Node(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    /**
     * When adding a new node, check whether the previous tail is logically removed.
     * If so, remove it physically from the linked list.
     */
    override fun enqueue(element: E) {
        while (true) {
            val newNode = Node(element)
            val currTail = tail.get()
            if (currTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currTail, newNode)
                return
            } else {
                tail.compareAndSet(currTail, currTail.next.get())
            }

            if (currTail.extractedOrRemoved) {
                currTail.remove()
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHead = head.get()
            val currHeadNext = currHead.next.get() ?: return null
            if (head.compareAndSet(currHead, currHeadNext)) {
                if (currHeadNext.markExtractedOrRemoved()) {
                    return currHeadNext.element
                }
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.get()
        while (true) {
            val next = node.next.get() ?: return false
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

    // TODO: Node is an inner class for accessing `head` in `remove()`
    private inner class Node(var element: E?) {
        val next = AtomicReference<Node?>(null)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = AtomicBoolean(false)
        val extractedOrRemoved: Boolean
            get() = _extractedOrRemoved.get()

        fun markExtractedOrRemoved(): Boolean {
            return _extractedOrRemoved.compareAndSet(false, true)
        }

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            /**
             * The removal procedure is split into two phases.
             * First, you need to mark the node as "extracted or removed".
             * On success, this node is logically removed, and the operation should return `true` at the end.
             */
            val isMarked = markExtractedOrRemoved()

            /**
             * In the second phase, the node should be removed physically,
             * updating the `next` field of the previous node to `this.next.value`.
             */
            val headNode = head.get()
            val tailNode = tail.get()

            /**
             * Do not remove `head` and `tail` physically to make the algorithm simpler.
             * In case a tail node is logically removed, it will be removed physically by `enqueue(..)`.
             */
            if (this != headNode && this != tailNode) {
                val previousNode = findPreviousNode(element) ?: return isMarked
                val nextNode = next.get()
                previousNode.next.set(nextNode)
            }

            return isMarked
        }

        private fun findPreviousNode(element: E?): Node? {
//            var fakeHead = head.get()
//            while (true) {
//                val next = fakeHead.next.get()
//                if (next == null) return null
//                if (next == element) return next
//                fakeHead = next.next.get()
//            }

            var node = head.get()
            while (true) {
                val next = node.next.get() ?: return null
                if (next == element) return next
                node = next
            }
        }
    }
}
