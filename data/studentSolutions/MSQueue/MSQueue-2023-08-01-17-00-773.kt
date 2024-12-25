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
        val newElement = MSQueue.Node(element)
        while (true) {
            val currentTail = tail.value
            if (currentTail.next.compareAndSet(null, newElement)) {
                tail.compareAndSet(currentTail, newElement)
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        val currentHead = head.value
        if (currentHead.next.value == null) return null
        if (head.compareAndSet(currentHead, currentHead.next.value!!)) {
            return currentHead.element
        }
        return null
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
