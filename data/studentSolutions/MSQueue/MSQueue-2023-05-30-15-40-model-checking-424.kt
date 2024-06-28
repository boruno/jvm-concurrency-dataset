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
        val newTail = Node(element)
        while (true) {
            val curTail = tail
            if (curTail.value.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail.value, newTail)
                return
            } else {
//                tail.compareAndSet(curTail.value, curTail.value.next.value ?: Node(null))
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val next = head.value.next.value ?: return null
            if (head.compareAndSet(curHead.value, next))
                return next.element
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
