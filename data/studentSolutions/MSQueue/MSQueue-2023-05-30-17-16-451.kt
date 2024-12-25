//package day1

import kotlinx.atomicfu.*
import kotlin.Exception

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
            val curTail = tail.value

            if (curTail.next.compareAndSet(null, newNode)) {
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value as Node<E>)
            }
        }
    }

    override fun dequeue(): E? {
//        TODO("implement me")

        while (true) {
            val curHead = head.value

            if (curHead.next.value == null) {
                throw Exception()
            }
            if (head.compareAndSet(curHead, curHead.next.value as Node<E>)) {
                return curHead.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
