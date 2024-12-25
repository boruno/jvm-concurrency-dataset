@file:Suppress("DuplicatedCode", "FoldInitializerAndIfToElvis")

//package day2

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
//        enqueueSequential(element)
        while (true) {
            val current = tail.value
            val nextNode = Node(element, prev = current)
            if (current.next.compareAndSet(null, nextNode)) {
                tail.compareAndSet(current, nextNode)
                // if (current.extractedOrRemoved) current.remove()
                return
            } else {
                tail.compareAndSet(current, current.next.value!!)
            }
        }
    }

    fun enqueueSequential(element: E) {
        val currentTail = tail.value
        val newNode = Node(element, prev = currentTail)
        tail.value.next.value = newNode
        tail.value = newNode
    }

    override fun dequeue(): E? {
        return dequeueSequential()
//        while (true) {
//            val current = head.value
//            val next = current.next.value ?: return null
//            if (head.compareAndSet(current, next)) {
//                if (next.remove()) return next.element
//            }
//        }
    }

    fun dequeueSequential(): E? {
        val currentHead = head.value
        val next = currentHead.next.value ?: return null
        head.value = next
        next.prev.value = null
        return next.element
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

        fun removeSequential(): Boolean {
            val removed = markExtractedOrRemoved()
            val prev = this.prev.value // return when 'this' is 'head'
            val next = this.next.value // return when 'this' is 'tail'

            if (prev != null) {
                // prevNext == this
                prev.next.value = next
                this.prev.value = null
            }

            if (next != null) {
                // nextPrev == this
                next.prev.value = prev
                this.next.value = null
            }

            return removed
        }

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean {
            return removeSequential()
            // val removed = markExtractedOrRemoved()
            // TODO: As in the previous task, the removal procedure is split into two phases.
            // TODO: First, you need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true` at the end.
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
            // TODO("Implement me!")
        }
    }
}