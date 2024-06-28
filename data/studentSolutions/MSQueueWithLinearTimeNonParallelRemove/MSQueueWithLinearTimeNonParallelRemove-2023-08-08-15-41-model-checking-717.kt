@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day2

import day1.MSQueue
import java.util.concurrent.atomic.*

class MSQueueWithLinearTimeNonParallelRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node>
    private val tail: AtomicReference<Node>

    init {
        val dummy = Node(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            // Create new node to add
            val node = Node(element)

            // Get snapshot of current tail node
            val currentTail = tail.get()
            // Only set a tail node if its NEXT node points to null
            // If it's not pointing to NULL, this means we have had an enqueue in between
            // And we need to move the tail note to the next node
            if (currentTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(currentTail, node)
                if (currentTail.extractedOrRemoved) {
                    currentTail.remove()
                }
                return
            } else {
                // Help a tail node to be in right position
                tail.compareAndSet(currentTail, currentTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
        // We retry as many times needed to perform successful operation
        while (true) {
            // Get current head
            val currentHead = head.get()
            // Element for dequeue is the one HEAD#next is pointing to
            // If it's non-existing return null
            val currentHeadNext = currentHead.next.get() ?: return null

            // Try to set HEAD node to next element in case state didn't change since we queried HEAD value
            if (head.compareAndSet(currentHead, currentHeadNext) && currentHeadNext.markExtractedOrRemoved()) {
                val element = currentHeadNext.element
                currentHeadNext.element = null
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
            val removed = markExtractedOrRemoved()

            if (head.get() == this || tail.get() == this) return removed

            val currentPreviousNode = findPreviousNode()
            val currentNext = next.get()
            currentPreviousNode?.next?.set(currentNext)
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
            return removed
        }

        fun findPreviousNode(): Node? {
            var nextNode = head.get().next.get()

            while (nextNode != null) {
                if (nextNode == this) return nextNode

                nextNode = nextNode.next.get()
            }

            return nextNode
        }
    }
}
