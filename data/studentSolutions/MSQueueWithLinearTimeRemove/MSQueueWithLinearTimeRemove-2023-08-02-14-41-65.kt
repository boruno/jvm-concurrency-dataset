@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

//package day2

import kotlinx.atomicfu.*

class MSQueueWithLinearTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node>

    init {
        val dummy = Node(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val newNode = Node(element)
            val currentTail = tail.value

            if (currentTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)
                return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentNext = currentHead.next.value ?: return null

            if (head.compareAndSet(currentHead, currentNext)) {
                if (currentNext.markExtractedOrRemoved()) {
                    return currentNext.element
                }
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = head.value
        while (true) {
            val next = node.next.value
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
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        var node = head.value
        // Traverse the linked list
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of this queue"
                }
            }
            node = node.next.value ?: break
        }
    }

    // TODO: Node is an inner class for accessing `head` in `remove()`
    private inner class Node(
        var element: E?
    ) {
        val next = atomic<Node?>(null)

        /**
         * TODO: Both [dequeue] and [remove] should mark
         * TODO: nodes as "extracted or removed".
         */
        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
//             TODO: The removal procedure is split into two phases.
//             TODO: First, you need to mark the node as "extracted or removed".
//             TODO: On success, this node is logically removed, and the
//             TODO: operation should return `true` at the end.
//             TODO: In the second phase, the node should be removed
//             TODO: physically, updating the `next` field of the previous
//             TODO: node to `this.next.value`.
//             TODO: Do not remove `head` and `tail` physically to make
//             TODO: the algorithm simpler. In case a tail node is logically removed,
//             TODO: it will be removed physically by `enqueue(..)`.
            val removed = markExtractedOrRemoved()
            while (true) {
                var curNext = next.value
                if (curNext == null) return removed

                val prev: Node = findPrev() ?: return removed
                if (curNext.extractedOrRemoved) {
                    curNext.remove()
                    curNext = curNext.next.value
                } else {
                    prev.next.value = curNext
                    break
                }
            }
            return removed
        }
        private fun findPrev(): Node? {
            var node: Node = head.value
            while (node.next.value != this) {
                val nextNode = node.next.value
                if (nextNode == null) return null
                if (nextNode.element == this) return nextNode
                node = node.next.value!!
            }
            return node
        }
    }
}
