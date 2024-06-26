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
        val newNode = Node(element)
        while (true) {
            val curTail = tail
            if (curTail.value.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail.value, newNode)
                return
            } else {
                curTail.value.next.value?.let { tail.compareAndSet(curTail.value, it) }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val curNext = head.value.next.value ?: return null
            if (head.compareAndSet(curHead.value, curNext)) {
                return curHead.value.element
            }

        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
