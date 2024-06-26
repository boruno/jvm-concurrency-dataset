@file:Suppress("FoldInitializerAndIfToElvis", "DuplicatedCode")

package day2

import kotlinx.atomicfu.*
import java.lang.IllegalStateException

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
            // try updating the last element
            val lastElement = tail.value
            // TODO: When adding a new node, check whether
            // TODO: the previous tail is logically removed.
            // TODO: If so, remove it physically from the linked list.
            if (lastElement.extractedOrRemoved) {
                lastElement.remove()
//                continue
            }

            val nextLastElement = Node(element)

            if (lastElement.next.compareAndSet(null, nextLastElement)) {
                // we successfully updated last element,
                // now it's time to move the tail
                if (tail.compareAndSet(lastElement, nextLastElement)) {
                    return // we have moved the tail
                } else {
                    return // tail was moved for us
                }
            } else {
                // we can't insert the last element, because new element was inserted
                // let's move the tail then
                val next = lastElement.next.value!!

                tail.compareAndSet(lastElement, next)
            }
        }

    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val nextHead = currentHead.next.value
                ?: return null // todo: empty stack should contain single dummy element

            if (head.compareAndSet(currentHead, nextHead)) {
                // TODO: After moving the `head` pointer forward,
                // TODO: mark the node that contains the extracting
                // TODO: element as "extracted or removed", restarting
                // TODO: the operation if this node has already been removed.
                if (nextHead.markExtractedOrRemoved()) {
                    return nextHead.element
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
    override fun checkNoRemovedElements() {
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
            // TODO: The removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            val wasNodeLogicallyRemovedByThisThread = markExtractedOrRemoved()
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true` at the end.
            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            val previousNode = findPreviousNode()
            previousNode.next.value = next.value // todo: CAS?

            return wasNodeLogicallyRemovedByThisThread

            // TODO: Do not remove `head` and `tail` physically to make
            // TODO: the algorithm simpler. In case a tail node is logically removed,
            // TODO: it will be removed physically by `enqueue(..)`.
        }

        private fun findPreviousNode(): Node {
            var current = head.value
            while (current.next.value != this) {
                current = current.next.value!!
            }
            return current
        }
    }
}
