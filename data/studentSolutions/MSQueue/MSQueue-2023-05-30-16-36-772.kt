//package day1

import kotlinx.atomicfu.*

class MSQueue<E> : Queue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        val dummy = Node<E>(null)
        val node = Node(element)
        tail.value.next.compareAndSet(dummy, node)
        tail.compareAndSet(dummy, node)
    }

    override fun dequeue(): E? {
        while (true) {
            val oldHead = head.value
            val newHead = oldHead.next.value ?: return null

            if (head.compareAndSet(oldHead, newHead)) {
                return head.value.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
