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
        while (true) {
            val nextTail = Node(element)
            val currentTail = tail.get()
            if (currentTail.next.compareAndSet(null, nextTail)) {
                tail.compareAndSet(currentTail, nextTail)

                // When adding a new node, check whether the previous tail is logically removed.
                if (currentTail.extractedOrRemoved) {
                    // If so, remove it physically from the linked list.
                    currentTail.remove()
                }
                return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.get()
            val nextHead = currentHead.next.get() ?: return null

            if (head.compareAndSet(currentHead, nextHead)) {
                val result = nextHead.element
                nextHead.element = null

                // After moving the `head` pointer forward,
                // mark the node that contains the extracting
                // element as "extracted or removed", restarting
                // the operation if this node has already been removed.
                if (nextHead.markExtractedOrRemoved()) {
                    return result
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
            // The removal procedure is split into two phases.
            // First, you need to mark the node as "extracted or removed".
            // On success, this node is logically removed, and the
            // operation should return `true` at the end.
            val result = markExtractedOrRemoved()

            // In the second phase, the node should be removed
            var previousNode = head.get()
            val currentTail = tail.get()

            // Do not remove `head` and `tail` physically to make
            // the algorithm simpler. In case a tail node is logically removed,
            // it will be removed physically by `enqueue(..)`.
            while (previousNode != null && previousNode != this && previousNode != currentTail) {
                val currentNode = previousNode.next.get()

                // physically, updating the `next` field of the previous
                // node to `this.next.value`.
                if (currentNode == this) {
                    val nextNode = this.next.get()

                    var removalCompleted = false
                    if (nextNode != null) { // check if tail or not
                        if (nextNode.extractedOrRemoved) {
                            nextNode.remove()
                        } else {
                            previousNode.next.compareAndSet(this, nextNode)
                            removalCompleted = true
                        }
                    }

                    if (removalCompleted) {
                        break
                    }
                }
                previousNode = currentNode
            }

            return result
        }
    }
}
