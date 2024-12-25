@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

//package day2

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class MSQueueWithLinearTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val dummy = Node(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        // TODO: When adding a new node, check whether
        // TODO: the previous tail is logically removed.
        // TODO: If so, remove it physically from the linked list.
        while(true) {
            val newNode = Node(element)
            val curTailNode = tail.get()
            if (curTailNode.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTailNode, newNode)
                if (curTailNode.extractedOrRemoved) {
                    curTailNode.remove()
                }
                return
            }

            val curTailNextNode = curTailNode.next.get()
            tail.compareAndSet(curTailNode, curTailNextNode)
        }
    }

    override fun dequeue(): E? {
        // After moving the `head` pointer forward,
        // mark the node that contains the extracting
        // element as "extracted or removed", restarting
        // the operation if this node has already been removed.
        while (true) {
            val curHead = head.get()
            val curHeadNext = curHead.next.get()
            curHeadNext ?: return null
            if (head.compareAndSet(curHead, curHeadNext)) {
                if (curHeadNext.markExtractedOrRemoved()) {
                    return curHeadNext.element
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

    // TODO: Node is an inner class for accessing `head` in `remove()`
    private inner class Node(
        var element: E?
    ) {
        val next = AtomicReference<Node?>(null)

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
            // TODO: The removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true` at the end.
            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            // TODO: Do not remove `head` and `tail` physically to make
            // TODO: the algorithm simpler. In case a tail node is logically removed,
            // TODO: it will be removed physically by `enqueue(..)`.
            val marked = markExtractedOrRemoved()
            val curPrevNode = findPrevNode()
            val curNextNode = next.get()
            val curTail = tail.get()
            if (curPrevNode != null && curTail != this) {
                curPrevNode.next.set(curNextNode)
            }
            return marked
        }

        private fun findPrevNode(): Node? {
            var curNode = head.get()
            while (true) {
                val nextNode = curNode.next.get()
                if (nextNode == this) return curNode
                if (nextNode == null) return null
                curNode = nextNode
            }
        }
    }
}
