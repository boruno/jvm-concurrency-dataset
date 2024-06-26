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
            val localTail = tail.value
            if (tail.compareAndSet(localTail, node)) {
                localTail.next.value = node
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val localHead = head.value
            if (localHead.element == null) return null

            val next = localHead.next.value!!
            if (head.compareAndSet(localHead, next)) {
                return localHead.element
            }
        }
        TODO("implement me")
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
