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
        val new = Node(element)
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, new)) {
                tail.value = new
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val next = head.value.next.value ?: return null

            val nextNext = next.next.value
            if (head.value.next.compareAndSet(next, nextNext)) {
                return next.element ?: throw IllegalStateException("null in queue")
            }
        }
    }

    private class Node<E>(
        val element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
