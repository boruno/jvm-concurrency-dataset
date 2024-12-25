//package day2

import kotlinx.atomicfu.*

class MSQueueWithOnlyLogicalRemove<E> : QueueWithRemove<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val newNode = Node(element)
            val currentTail = tail
            val value = currentTail.value
            val next = value.next
            if (next.compareAndSet(null, newNode)) {
                tail.compareAndSet(value, newNode)
                return
            }
            else {
                val valueNext = next.value
                if (valueNext != null) {
                    tail.compareAndSet(value, valueNext)
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val value = curHead.value
            val update = value.next.value ?: break
            if (head.compareAndSet(value, update)) {
                if (value.markExtractedOrRemoved()) {
//                    return curHead.value.element as E
                    return update.element
                }
            }
        }
        return null
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
        // In this version, we allow storing
        // removed elements in the linked list.
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)

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
            return _extractedOrRemoved.compareAndSet(false, true)
            // TODO: You need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true`.
            // TODO: Otherwise, the node is already either extracted or removed,
            // TODO: so the operation should return `false`.
//            TODO("Implement me!")
        }
    }
}