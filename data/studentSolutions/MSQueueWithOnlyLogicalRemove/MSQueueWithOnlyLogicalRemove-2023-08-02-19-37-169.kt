package day2

import day1.MSQueue
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
        val node = Node<E>(element)
        var localTail: Node<E>
        var next: Node<E>?
        while (true) {
            localTail = tail.value
            next = localTail.next.value
            if (localTail == tail.value) {
                if (next == null) {
                    if (localTail.next.compareAndSet(null, node)) {
                        break
                    }
                } else {
                    tail.compareAndSet(localTail, next)
                }
            }
        }
        tail.compareAndSet(localTail, node)
    }

    override fun dequeue(): E? {
        var localHead: Node<E>
        var localTail: Node<E>
        var next: Node<E>?
        var value: E?
        while (true) {
            localHead = head.value
            localTail = tail.value
            next = localHead.next.value
            if (localHead == head.value) {
                if (localHead == localTail) {
                    if (next == null)
                    {
                        return null
                    }
                    tail.compareAndSet(localTail, next)
                }
                else {
                    value = next?.element
                    if (next != null) {
                        if (head.compareAndSet(localHead, next)) {
                            if (head.value.markExtractedOrRemoved())
                            {
                                break
                            };
                        }
                    }
                }
            }
        }
        return value
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
            return markExtractedOrRemoved()
        }
    }
}