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
        val newTail = Node(element)
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                return
            } else {
//                val shiftedTail = requireNotNull(curTail.next.value)
//                tail.compareAndSet(curTail, shiftedTail)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val newHead = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, newHead)) return newHead.element
        }
    }

    private class Node<E>(
        val element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
