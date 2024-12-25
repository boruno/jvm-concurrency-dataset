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
        while (true) {
            val oldTail: Node<E> = tail.value
            val newTail: Node<E> = Node(element)
            if (oldTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(oldTail, newTail)
                return
            } else {
                tail.compareAndSet(oldTail, oldTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val oldHead: Node<E> = head.value
            val newHead: Node<E> = oldHead.next.value ?: return null

            if (head.compareAndSet(oldHead, newHead)) {
                return oldHead.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
