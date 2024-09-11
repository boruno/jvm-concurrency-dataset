@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day2

import kotlinx.atomicfu.*

class MSQueueWithConstantTimeRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(element = null, prev = null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            // try updating the last element
            val lastElement = tail.value
            val nextLastElement = Node(element, prev = lastElement)

            if (lastElement.next.compareAndSet(null, nextLastElement)) {
                // we successfully updated last element,
                // now it's time to move the tail
                if (tail.compareAndSet(lastElement, nextLastElement)) {
                    // we have moved the tail
                } else {
                    // tail was moved for us
                }

                // TODO: When adding a new node, check whether
                // TODO: the previous tail is logically removed.
                // TODO: If so, remove it physically from the linked list.
                if (lastElement.extractedOrRemoved) {
                    lastElement.remove()
                }
                return
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
        check(head.value.prev.value == null) {
            "`head.prev` must be null"
        }
        check(tail.value.next.value == null) {
            "tail.next must be null"
        }
        // Traverse the linked list
        var node = head.value
        while (true) {
            if (node !== head.value && node !== tail.value) {
                check(!node.extractedOrRemoved) {
                    "Removed node with element ${node.element} found in the middle of the queue"
                }
            }
            val nodeNext = node.next.value
            // Is this the end of the linked list?
            if (nodeNext == null) break
            // Is next.prev points to the current node?
            val nodeNextPrev = nodeNext.prev.value
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
        val next = atomic<Node<E>?>(null)
        val prev = atomic(prev)

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
            // TODO: As in the previous task, the removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true` at the end.
            val wasNodeLogicallyRemovedByThisThread = markExtractedOrRemoved()

            // TODO: In the second phase, the node should be removed
            // TODO: physically, updating the `next` field of the previous
            // TODO: node to `this.next.value`.
            // TODO: In this task, you have to maintain the `prev` pointer,
            // TODO: which references the previous node. Thus, the `remove()`
            // TODO: complexity becomes O(1) under no contention.
            // TODO: Do not remove physical head and tail of the linked list;
            // TODO: it is totally fine to have a bounded number of removed nodes
            // TODO: in the linked list, especially when it significantly simplifies
            // TODO: the algorithm.
            val prev = this.prev.value
            val next = this.next.value

            // prev != null && next != null

            if (prev != null && next != null) {
                next.prev.compareAndSet(this, prev)
                prev.next.compareAndSet(this, next)

                if (prev.extractedOrRemoved) {
                    prev.remove()
                }

                if (next.extractedOrRemoved) {
                    next.remove()
                }
            }

            return wasNodeLogicallyRemovedByThisThread

            // TODO: Do not remove `head` and `tail` physically to make
            // TODO: the algorithm simpler. In case a tail node is logically removed,
            // TODO: it will be removed physically by `enqueue(..)`.

        }
    }
}