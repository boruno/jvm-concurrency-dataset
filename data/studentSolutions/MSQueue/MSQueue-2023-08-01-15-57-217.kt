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
            val node = Node<E>(element)
            val currentTail = tail

            if (currentTail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(currentTail.value, node)
                return
            } else {
                tail.compareAndSet(currentTail.value, currentTail.value.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head
            val currentHeadNext = currentHead.value.next

            if (currentHeadNext.value != null) {
                error("XD")
            }

            val currentHeadValue = currentHeadNext.value
            if (head.compareAndSet(currentHead.value, currentHeadValue!!)) {
                return currentHeadValue.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
