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
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, curHeadNext))
                return curHead.element
        }
    }

    private class Node<E>(
        val element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
