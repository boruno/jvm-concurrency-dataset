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
            val newTail = Node(element)
            val expectedTail = tail.value
            val expectedTailNext = expectedTail.next
            if (expectedTailNext.compareAndSet(null, newTail)) {
                // expectedTail's next was null. So it was the real tail.
                tail.compareAndSet(expectedTail, newTail)
                return
            }
            // Wait a minute. expectedTail's next is not null. tail probably is not the real tail.
            // Try to move tail forward.
            tail.compareAndSet(expectedTail, expectedTailNext.value!!)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val expectedHead = head.value
            val newHead = expectedHead.next.value ?: return null
            head.compareAndSet(expectedHead, newHead)
            return newHead.element
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
