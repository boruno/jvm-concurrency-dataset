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
            val newTail = Node(element)
            val expectedTail = tail.value
            val expectedTailNext = expectedTail.next
            if (expectedTailNext.compareAndSet(null, newTail)) {
                actualizeTail()
                return
            }
            // Help out and try again.
            // actualizeTail()
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val expectedHead = head.value
            val newHead = expectedHead.next.value ?: return null
            if (head.compareAndSet(expectedHead, newHead))
                return newHead.element
        }
    }

    private fun actualizeTail() {
        while (true) {
            val expectedTail = tail.value
            // If expectedTail's next is null, we're done.
            val tailNext = expectedTail.next.value ?: return
            tail.compareAndSet(expectedTail, tailNext)
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
