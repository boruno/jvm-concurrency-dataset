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
            val newElement = Node(element)
            val nextValue = tail.value.next
            val currentTail = tail
            if (nextValue.compareAndSet(null, newElement)) {
                tail.compareAndSet(currentTail.value, newElement)
                return
            } else {
                tail.compareAndSet(currentTail.value, currentTail.value.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curValue = head.value
            val next = head.value.next
            if (next.value == null) throw RuntimeException()
            if (head.compareAndSet(curValue, next.value!!)) {
                return next.value?.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
