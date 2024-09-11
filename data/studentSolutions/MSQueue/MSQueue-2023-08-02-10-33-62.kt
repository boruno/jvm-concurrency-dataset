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
            val currentTail = tail.value
            val newTail = Node(element)
            if (currentTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(currentTail, newTail)
                return
            } else {
                // `currentTail.next.value` cannot be null here.
                // Otherwise, previous `compareAndSet` should be successful
//                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val nextHead = currentHead.next.value ?: return null
            if (head.compareAndSet(currentHead, nextHead)) {
                return nextHead.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
