package day2

import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReference

@Suppress("DuplicatedCode")
class MSQueueWithOnlyLogicalRemove<E> : QueueWithRemove<E> {
    private val headRef: AtomicReference<Node<E>>
    private val tailRef: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        headRef = AtomicReference(dummy)
        tailRef = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        val node = Node(element)
        while (true) {
            val tail = tailRef.get()
            val witness = tail.next.compareAndExchange(null, node)
            if (witness === null) {
                // this thread published the node
                tailRef.compareAndSet(tail, node)
                return
            } else {
                tailRef.compareAndSet(tail, witness)
                // loop again
            }
        }
    }

    override fun dequeue(): E? {
        var head = headRef.get()
        while (true) {
            val next = head.next.get() ?: return null
            val witness = headRef.compareAndExchange(head, next)
            if (witness === head) {
                // this thread moved the pointer forward
                val result = next.element
                next.element = null // this node is now a dummy
                return result
            } else {
                head = witness
            }
        }
    }

    override fun remove(element: E): Boolean {
        // Traverse the linked list, searching the specified
        // element. Try to remove the corresponding node if found.
        // DO NOT CHANGE THIS CODE.
        var node = headRef.get()
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
    override fun checkNoRemovedElements() {
        // In this version, we allow storing
        // removed elements in the linked list.
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>?>(null)

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
            // TODO: You need to mark the node as "extracted or removed".
            // TODO: On success, this node is logically removed, and the
            // TODO: operation should return `true`.
            // TODO: Otherwise, the node is already either extracted or removed,
            // TODO: so the operation should return `false`.
            TODO("Implement me!")
        }
    }
}