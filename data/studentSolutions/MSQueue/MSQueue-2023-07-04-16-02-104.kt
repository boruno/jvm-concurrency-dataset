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
        val newNode = Node(element)
        val currentTail = tail.value
        val next = currentTail.next
        if (next.compareAndSet(null, newNode)) {
            tail.compareAndSet(currentTail, newNode)
        }
        else {
            tail.compareAndSet(currentTail, next.value!!)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val next = currentHead.next.value ?: return null
            if (head.compareAndSet(currentHead, next)) {
                return next.element
            }
        }
    }

    // TODO task* занулять после dequeue, чтобы gc мог собрать

    // TODO task*: check how iterator works in concurrent collections

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
