package day1

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
            if (tail.next.compareAndSet(null, node) &&
                this.tail.compareAndSet(tail, node)) {
                this.head.value.next.compareAndSet(null, node)
                break
            }
            val next = tail.next.value ?: continue
            this.tail.compareAndSet(tail, next)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val head = head.value
            val next = head.next.value ?: Node<E>(null)
            if (this.head.compareAndSet(head, next)) {
                return head.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
