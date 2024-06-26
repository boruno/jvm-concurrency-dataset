package day1

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

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
        val curTail = tail.value
        if (curTail.next.compareAndSet(null, newNode)) {
            if (tail.compareAndSet(curTail, newNode)) return
        } else tail.compareAndSet(curTail, curTail.next.value!!)
        enqueue(element)
    }

    override fun dequeue(): E? {
        val curHead = head.value
        val nextHead = curHead.next.value ?: return null
        if (head.compareAndSet(curHead, nextHead)) return nextHead.element
        return dequeue()
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
