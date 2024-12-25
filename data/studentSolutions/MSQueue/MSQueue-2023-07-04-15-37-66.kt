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
            val currentTail = tail.value
            val nextElement =  Node(element)
            if (currentTail.next.compareAndSet(null, nextElement)) {
                tail.compareAndSet(tail.value, nextElement)
            } else {
                tail.compareAndSet(tail.value, currentTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value
            if (currentHeadNext == null) throw IllegalArgumentException()
            if (head.compareAndSet(currentHead, currentHeadNext)) {
                return currentHeadNext.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
