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
        val new = Node(element)
        val curTail = tail.value
        if (curTail.next.compareAndSet(null, new)) {
            tail.compareAndSet(curTail, new)
        } else {
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }

    override fun dequeue(): E? {
        val curHead = head.value
        val chNext = head.value.next.value ?: return null
        return if (head.compareAndSet(curHead, chNext)) chNext.element else null
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
