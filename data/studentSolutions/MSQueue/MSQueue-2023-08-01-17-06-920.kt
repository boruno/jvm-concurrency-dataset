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
        while (true)
        {
            val last = getTail()
            if (last.next.compareAndSet(null, node))
                return
        }
    }

    override fun dequeue(): E? {
        while (true)
        {
            val first = head.value
            val next = first.next.value ?: return null
            if (head.compareAndSet(first, next))
                return first.element
        }
    }

    private fun getTail(): Node<E>
    {
        while (true) {
            val result = tail.value
            val next = result.next.value ?: return result
            tail.compareAndSet(result, next)
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
