package day1

import kotlinx.atomicfu.*
import java.lang.StringBuilder

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
        val newTail = Node(element)
        while (true) {
            val currentTail  = tail.value
            if (currentTail.next.compareAndSet(null, newTail)){
                tail.compareAndSet(currentTail, newTail)
                println("Enqueue $element" + toString())
                return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    @Synchronized
    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentNext = currentHead.next.value ?: throw IllegalStateException("Queue is empty")
            if (head.compareAndSet(currentHead, currentNext)){
                val result = currentHead.element
                println("Dequeued $result" + toString())
                return result
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }

    override fun toString(): String {
        val builder = StringBuilder(" | ")
        var current = head.value
        while (current != tail.value) {
            builder.append(current.element)
            builder.append(" ")
            current = current.next.value!!
        }
        builder.append(" | ${hashCode()}")
        return builder.toString()
    }
}
