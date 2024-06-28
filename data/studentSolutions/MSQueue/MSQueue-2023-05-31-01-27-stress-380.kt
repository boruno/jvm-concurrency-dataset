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
        while (true) {
            val node = Node(element)
            val currentTail  = tail
            if (currentTail.value.next.compareAndSet(null, node)){
                tail.compareAndSet(currentTail.value, node)
                println("Enqueue $element" + toString())
                return
            } else {
                tail.compareAndSet(currentTail.value, currentTail.value.next.value!!)
            }
        }
    }

    @Synchronized
    override fun dequeue(): E? {
        while (true) {
            val currentHead = head
            val currentNext = currentHead.value.next.value ?: throw IllegalStateException("Queue is empty")
            if (head.compareAndSet(currentHead.value, currentNext)){
                val result = currentHead.value.element
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
