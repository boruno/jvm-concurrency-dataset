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
        val node = Node(element)
        while (true) {
            val t = tail.value
            if (t.next.compareAndSet(null, node)) {
                tail.compareAndSet(t, node)
                return
            } else {
                tail.compareAndSet(t, t.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val current = head.value
            val next = current.next.value
            if (next == null) return null
            if (head.compareAndSet(current, next)) return current.element
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
