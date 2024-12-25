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
            val node = Node(element)
            val currentTail = tail.value
            val next = currentTail.next
            if (next.compareAndSet(null, node)) {
                tail.compareAndSet(currentTail, node)
                return
            } else {
                tail.compareAndSet(currentTail, next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head
            val currentHeadNext = currentHead.value.next

            val currentHeadValue = currentHeadNext.value ?: return null

            if (head.compareAndSet(currentHead.value, currentHeadValue)) {
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
