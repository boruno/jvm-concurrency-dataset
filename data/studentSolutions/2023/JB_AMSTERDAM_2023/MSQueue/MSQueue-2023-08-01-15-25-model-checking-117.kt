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
        while (true) {
            val curTail = tail.value
            val newTail = Node(element)
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
            } else {
                val existingNext = curTail.next.value!!
                tail.compareAndSet(curTail, existingNext)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curNext = curHead.next.value
            if (curNext == null) {
                return null
            }
            if (head.compareAndSet(curHead, curNext)) {
                return curNext.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
