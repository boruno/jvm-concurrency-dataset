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
        val newHead = Node(element)
        while (true) {
            val currentHead = head.value
            if (currentHead.next.compareAndSet(null, newHead)) {
                tail.compareAndSet(currentHead, newHead)
            }
            else {
                tail.compareAndSet(currentHead, currentHead.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curTail = tail.value
            val newTail = curTail.next.value ?: throw IllegalArgumentException()
            if (tail.compareAndSet(curTail, newTail)) {
                return newTail.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
