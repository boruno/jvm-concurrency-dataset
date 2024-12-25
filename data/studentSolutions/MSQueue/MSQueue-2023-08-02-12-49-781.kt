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
            val node = Node(element)
            val tail = this.tail.value
            if (tail.next.compareAndSet(null, node)) {
                this.tail.compareAndSet(tail, node)
                break
            } else {
                val next = this.tail.value.next.value ?: continue
                this.tail.compareAndSet(tail, next)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val element = head.value.element
            val head = head.value
            val next = head.next.value
            if (next != null && this.head.compareAndSet(head, next)) {
                return element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
