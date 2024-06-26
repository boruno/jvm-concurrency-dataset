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
            val currentTail  = tail
            if (currentTail.value.next.compareAndSet(null, node)){
                tail.compareAndSet(currentTail.value, node)
                return
            } else {
                tail.compareAndSet(currentTail.value, currentTail.value.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentNext = currentHead.next.value ?: throw IllegalStateException("Queue is empty")
            if (head.compareAndSet(currentHead, currentNext)){
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
