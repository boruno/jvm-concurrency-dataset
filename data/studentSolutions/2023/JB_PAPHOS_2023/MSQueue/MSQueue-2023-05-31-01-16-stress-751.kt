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

    @Synchronized
    override fun enqueue(element: E) {
        println("Enqueue $element")
        while (true) {
            val node = Node(element)
            val currentTail  = tail
            if (currentTail.value.next.compareAndSet(null, node)){
                tail.compareAndSet(currentTail.value, node)
                return
            } else {
                tail.compareAndSet(currentTail.value, currentTail.value.next.value!!)
            }
        }
    }

    @Synchronized
    override fun dequeue(): E? {
        println("Dequeue")
        while (true) {
            val currentHead = head
            val currentNext = currentHead.value.next.value ?: throw IllegalStateException("Queue is empty")
            if (head.compareAndSet(currentHead.value, currentNext)){
                return currentHead.value.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
