package day1

import kotlinx.atomicfu.*
import java.util.NoSuchElementException

class MSQueue<E> : Queue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        val newNode = Node(element)
        while (true) {
            val currentTail = tail.value
            if (currentTail.next.compareAndSet(null, newNode))
                tail.compareAndSet(currentTail, newNode)
            else {
                currentTail.next.value?.let { next ->
                    tail.compareAndSet(currentTail, next)
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val next = currentHead.next.value ?: return null
            if (head.compareAndSet(currentHead, next))
                return next.element
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
