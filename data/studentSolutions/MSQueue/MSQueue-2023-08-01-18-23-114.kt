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
            val n = Node(element)
            val currentTail = tail.value
            println(currentTail.element)
            println(currentTail.next.value?.element)
            if (currentTail.next.compareAndSet(null, n)) {
                tail.compareAndSet(currentTail, n)
                return
            } else {
                tail.compareAndSet(currentTail, tail.value.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentDummyHead =  head.value
            val currentHead = currentDummyHead.next.value
                ?: return null // queue is empty
            if (head.value.next.compareAndSet(currentHead, currentHead.next.value)) {
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
