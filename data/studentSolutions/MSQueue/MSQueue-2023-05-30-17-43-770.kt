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
        val currTail = tail.value
        val next = currTail.next
        if (next.compareAndSet(null, newNode)) {
            tail.compareAndSet(currTail, newNode)
            return
        }
        else {
            val newCurrTail = tail.value
            val v = newCurrTail.next.value
            if (v != null) tail.compareAndSet(currTail, v)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHead = head.value
            val currHeadNext = currHead.next.value
            if (currHeadNext == null) return null
            if (head.compareAndSet(currHead, currHeadNext))
                return currHeadNext.element
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
