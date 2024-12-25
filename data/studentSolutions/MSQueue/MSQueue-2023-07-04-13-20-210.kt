//package day1

import kotlinx.atomicfu.*

class MSQueue<E> : Queue<E> {
    private val head: Node<E>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = dummy
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val current = tail.value
            val next = current.next.value
            if (next != null) {
                tail.compareAndSet(current, next)
                continue
            }
            val newNode = Node(element)
            if (current.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(current, newNode)
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val next = head.next.value ?: return null
            if (head.next.compareAndSet(next, next.next.value)) {
                return next.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
