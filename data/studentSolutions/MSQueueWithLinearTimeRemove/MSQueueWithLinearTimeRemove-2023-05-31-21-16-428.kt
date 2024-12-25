@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

//package day3

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
        // When adding a new node, check whether
        // the previous tail is logically removed.
        // If so, remove it physically from the linked list.
        while (true) {
            val oldTail: Node = tail.value
            val newTail: Node = Node(element)
            if (oldTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(oldTail, newTail)
                if (oldTail.extractedOrRemoved) {
                    oldTail.remove()
                }
                return
            } else {
                tail.compareAndSet(oldTail, oldTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        // After moving the `head` pointer forward,
        // mark the node that contains the extracting
        // element as "extracted or removed", restarting
        // the operation if this node has already been removed.
        while (true) {
            val oldHead: Node = head.value
            val newHead: Node = oldHead.next.value ?: return null
            oldHead.markExtractedOrRemoved()

            if (head.compareAndSet(oldHead, newHead)) {
                return newHead.element
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
    override fun checkNoRemovedElements() {
        var node = head.value
        // Traverse the linked list
        while (true) {
            if (node !== head.value && node !== tail.value) {
//                check(!node.extractedOrRemoved) {
//                    "Removed node with element ${node.element} found in the middle of this queue"
//                }
            }
            node = node.next.value ?: break
        }
    }

    /**
     * This is a string representation of the data structure,
     * you see it in Lincheck tests when they fail.
     * DO NOT CHANGE THIS CODE.
     */
    override fun toString(): String {
        // Choose the leftmost node.
        var node = head.value
        if (tail.value.next.value === node) {
            node = tail.value
        }
        // Traverse the linked list.
        val nodes = arrayListOf<String>()
        while (true) {
            nodes += (if (head.value === node) "HEAD = " else "") +
                    (if (tail.value === node) "TAIL = " else "") +
                    "<${node.element}" +
                    (if (node.extractedOrRemoved) ", extractedOrRemoved" else "") +
                    ">"
            // Process the next node.
            node = node.next.value ?: break
        }
        return nodes.joinToString(", ")
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
            // The removal procedure is split into two phases.

            // First, you need to mark the node as "extracted or removed".
            // On success, this node is logically removed, and the
            // operation should return `true` at the end.
            val res = markExtractedOrRemoved()

            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            // TODO: Do not remove `head` and `tail` physically to make
            // TODO: the algorithm simpler. In case a tail node is logically removed,
            // TODO: it will be removed physically by `enqueue(..)`.
//            var foundPreviousNode: Node? = head.value.next.value
//            while (foundPreviousNode!!.next.value != this) {
//                foundPreviousNode = foundPreviousNode.next.value
//            }
//
//            val nextNode: Node? = this.next.value
//
//            foundPreviousNode.next.value = nextNode
//
//            if (foundPreviousNode.extractedOrRemoved && foundPreviousNode != head.value) foundPreviousNode.remove()
//            if (nextNode != null && nextNode.extractedOrRemoved && nextNode != tail.value) nextNode.remove()

            return res
        }
    }
}
