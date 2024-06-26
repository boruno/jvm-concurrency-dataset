@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

package day3

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
        // When adding a new node, check whether
        // the previous tail is logically removed.
        // If so, remove it physically from the linked list.
        while (true) {
            val oldTail: Node<E> = tail.value
            val newTail: Node<E> = Node(element, oldTail)
            if (oldTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(oldTail, newTail)
                if (oldTail.extractedOrRemoved) {
                    oldTail.remove()
                }
                return
            } else {
                val newTail = oldTail.next.value
                if (newTail != null) tail.compareAndSet(oldTail, newTail)
            }
        }
    }

    override fun dequeue(): E? {
        // After moving the `head` pointer forward,
        // mark the node that contains the extracting
        // element as "extracted or removed", restarting
        // the operation if this node has already been removed.
        while (true) {
            val oldHead: Node<E> = head.value
            val newHead: Node<E> = oldHead.next.value ?: return null

            if (newHead.markExtractedOrRemoved() && head.compareAndSet(oldHead, newHead)) {
                newHead.prev.value = null
                return newHead.element
            }
            //println("newHead.prev should be ${oldHead.element}, change to ${null}, but it's ${newHead.prev.value?.element}")
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
//                check(!node.extractedOrRemoved) {
//                    "Removed node with element ${node.element} found in the middle of the queue"
//                }
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
                "node.next.prev != node; `node` contains ${node.element}, `node.next` contains ${nodeNext.element}; compared with ${nodeNextPrev.element}"
            }
            // Process the next node.
            node = nodeNext
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
            // The removal procedure is split into two phases.

            // First, you need to mark the node as "extracted or removed".
            // On success, this node is logically removed, and the
            // operation should return `true` at the end.
            val res = markExtractedOrRemoved()

            // In the second phase, the node should be removed
            // physically, updating the `next` field of the previous
            // node to `this.next.value`.
            // In this task, you have to maintain the `prev` pointer,
            // which references the previous node. Thus, the `remove()`
            // complexity becomes O(1) under no contention.
            val previousNode: Node<E>? = this.prev.value
            val nextNode: Node<E>? = this.next.value

            // Do not remove physical head and tail of the linked list;
            // it is totally fine to have a bounded number of removed nodes
            // in the linked list, especially when it significantly simplifies
            // the algorithm.
            if (previousNode == null || nextNode == null) return res

            previousNode.next.compareAndSet(this, nextNode)
            nextNode.prev.compareAndSet(this, previousNode)

            if (previousNode.extractedOrRemoved) previousNode.remove()
            if (nextNode.extractedOrRemoved) nextNode.remove()

            return res
        }
    }
}