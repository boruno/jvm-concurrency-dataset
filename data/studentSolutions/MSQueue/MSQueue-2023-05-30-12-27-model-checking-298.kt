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
            val oldTail = tail.value
            if (oldTail.next.compareAndSet(null, newTail)) {
                tail.value = newTail
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val dummy = head.value
            val nextHead = dummy.next.value ?: return null
            if (head.compareAndSet(dummy, nextHead))
                return nextHead.element
        }
    }

    private class Node<E>(var element: E?) {
        val next = atomic<Node<E>?>(null)
    }
}
