//package day1

import kotlinx.atomicfu.*
import java.lang.RuntimeException

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
            tail.compareAndSet(curTail, newNode)
            return
        } else {
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: throw RuntimeException(
                "Unexpected value of current head next element")
            if (head.compareAndSet(curHead, curHeadNext)) {
                return curHeadNext.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
