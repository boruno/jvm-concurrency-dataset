package day1

import kotlinx.atomicfu.*
import java.lang.Exception

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
            val currentTail: Node<E> = tail.value
            val nextTail: Node<E>? = currentTail.next.value

            if (currentTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)
                return
            } else  {
                if (currentTail == tail.value && nextTail == null)
                    throw Exception("aha")
                tail.compareAndSet(currentTail, nextTail!!)
            }

        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead: Node<E> = head.value
            val next: Node<E> = currentHead.next.value ?: return null

            if (head.compareAndSet(currentHead, next))
                return next.element
        }

    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
