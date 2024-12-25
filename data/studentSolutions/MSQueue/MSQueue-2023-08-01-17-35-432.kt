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
        val next = Node(element)

        while(true) {
            val currentTailValue = tail.value
            if (currentTailValue.next.compareAndSet(null, next)) {
                tail.compareAndSet(currentTailValue, next)
                return
            } else {
                currentTailValue.next.value?.let {
                    tail.compareAndSet(currentTailValue, it)
                }
            }
        }
    }

    override fun dequeue(): E? {
        val headValue = head.value
        val next = headValue.next.value ?: return null

        if(head.compareAndSet(headValue, next)) {
            return next.element
        }
        return null
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
