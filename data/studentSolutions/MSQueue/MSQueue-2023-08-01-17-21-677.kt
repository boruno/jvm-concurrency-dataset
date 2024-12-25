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
                return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentNext = currentHead.next
//            if (currentNext.value == null) return null
            if (head.compareAndSet(currentHead, currentNext.value!!)) {
                return currentHead.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
