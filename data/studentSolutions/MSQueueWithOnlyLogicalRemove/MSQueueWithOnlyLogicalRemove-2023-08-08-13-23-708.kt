//package day2

import MSQueue
import java.util.concurrent.atomic.*

class MSQueueWithOnlyLogicalRemove<E> : QueueWithRemove<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        // IN
        while (true) {
            val curTail = tail.get()
            val newNode = Node(element)
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                return
            } else {
                // we need to help here other thread
                tail.compareAndSet(curTail, curTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        // TODO: Copy your implementation.
        // TODO:
        // TODO: After moving the `head` pointer forward,
        // TODO: mark the node that contains the extracting
        // TODO: element as "extracted or removed", restarting
        // TODO: the operation if this node has already been removed.
        while (true) {
            val curHead = head.get()
            val curHeadNext = curHead.next.get() ?: return null
            if (head.compareAndSet(curHead, curHeadNext)) {
                // we have return in the 2-nd if to take into account whether an element was logically removed
                // if tryMarkExtracted(): return element
                if (curHead.remove()) {
                    return curHeadNext.element
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
        // In this version, we allow storing
        // removed elements in the linked list.
        check(tail.get().next.get() == null) {
            "`tail.next` must be `null`"
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>?>(null)

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
            // TODO: You need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true`.
            // TODO: Otherwise, the node is already either extracted or removed,
            // TODO: so the operation should return `false`.
            return markExtractedOrRemoved()
        }
    }
}