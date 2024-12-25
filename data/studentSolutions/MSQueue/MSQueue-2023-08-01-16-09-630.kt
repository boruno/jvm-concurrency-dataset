//package day1

import kotlinx.atomicfu.*
import java.lang.RuntimeException
import java.util.EmptyStackException

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
            val newNode = Node(element)
            val currentTail = tail.value
            val currentNext = currentTail.next.value
            if (tail.value.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, currentNext!!)
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val dummy = head.value
            val currentHead = dummy.next.value ?: return null
            val nextHead = currentHead.next.value
            if (head.value.next.compareAndSet(currentHead, nextHead)) {
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
