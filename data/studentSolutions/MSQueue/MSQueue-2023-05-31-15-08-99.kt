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
        val node = Node(element)
        while (true) {
            val last = tail.value
            val next = last.next.value
            if (last == tail.value) {
                if (next == null) {
                    tail.value.next.compareAndSet(null, node)
                    return
                } else {
                    tail.compareAndSet(tail.value, node)
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val start = head.value
            val next = start.next.value
            if (start == head.value) {
                if (next == null) {
                    return null
                }
                if (head.compareAndSet(start, next)) {
                    return start.element
                }
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
