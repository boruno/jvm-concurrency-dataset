@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

import java.util.concurrent.atomic.*

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
            val currentTail = tail.get()
            val nextTail = Node(element, currentTail)

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
                nextHead.prev.set(null) // current head is removed node and prev is dummy

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
            val nodeNext = node.next.get()
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
        val next = AtomicReference<Node<E>?>(null)
        val prev = AtomicReference(prev)

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

            while (true) {
                // In the second phase, the node should be removed
                val previousNode = this.prev.get()

                // Do not remove `head` and `tail` physically to make
                // the algorithm simpler. In case a tail node is logically removed,
                // it will be removed physically by `enqueue(..)`.
                if (previousNode == null) { // if previous node is null then current is head so don't remove it
                    break
                }

                // previous node could be dummy

                val nextNode = this.next.get()

                if (nextNode == null) { // if next node is null then current is tail so don't remove it
                    break
                }


                previousNode.next.compareAndSet(this, nextNode)
                nextNode.prev.compareAndSet(this, previousNode)

                if (previousNode.extractedOrRemoved) {
                    previousNode.remove()
                }
                if (nextNode.extractedOrRemoved) {
                    nextNode.remove()
                }
                break
            }

            return result
        }
    }
}