package day2

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
            val node = Node(element)
            val currentTail = tail.value
            val next = currentTail.next
            if (next.compareAndSet(null, node)) {
                if (tail.compareAndSet(currentTail, node)) {
                    return
                }
            } else {
                tail.compareAndSet(currentTail, next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next

            val currentHeadValue = currentHeadNext.value ?: return null

            if (head.compareAndSet(currentHead, currentHeadValue)) {
                if (!currentHeadValue.extractedOrRemoved) {
                    if (currentHeadValue.markExtractedOrRemoved()) {
                        return currentHeadValue.element
                    }
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
        // In this version, we allow storing
        // removed elements in the linked list.
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)

        private val _extractedOrRemoved = atomic(false)
        val extractedOrRemoved get() = _extractedOrRemoved.value

        fun markExtractedOrRemoved(): Boolean = _extractedOrRemoved.compareAndSet(false, true)

        /**
         * Removes this node from the queue structure.
         * Returns `true` if this node was successfully
         * removed, or `false` if it has already been
         * removed by [remove] or extracted by [dequeue].
         */
        fun remove(): Boolean = markExtractedOrRemoved()
    }
}