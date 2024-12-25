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
        val newNode = Node(element)
        val currentTail = tail.value
        if (tail.value.next.compareAndSet(null, newNode)) {
            tail.compareAndSet(currentTail, newNode)
        } else {
            currentTail.next.value?.let {
                tail.compareAndSet(currentTail, it)
            }
        }
    }

    override fun dequeue(): E? {
        return null
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
