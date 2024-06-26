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

        var curTail = tail.value
        while (!curTail.next.compareAndSet(null, newNode)) {
            tail.compareAndSet(curTail, curTail.next.value!!)
            curTail = tail.value
        }
        tail.compareAndSet(curTail, curTail.next.value!!)
    }

    override fun dequeue(): E? {
        var curHead = head.value
        curHead.next.value ?: return null
        while (!head.compareAndSet(curHead, curHead.next.value!!)) {
            curHead = head.value
            if (curHead.next.value == null)
                return null
        }
        return curHead.element
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
