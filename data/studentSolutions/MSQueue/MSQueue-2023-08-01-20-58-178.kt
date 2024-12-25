//package day1

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

    //add
    override fun enqueue(element: E) {
        val newNode = Node(element)
        while (true) {
            val currentTail = tail
            val value = currentTail.value
            val next = value.next
            if (next.compareAndSet(null, newNode)) {
                tail.compareAndSet(value, newNode)
                return
            } else {
                next.value?.let { tail.compareAndSet(value, it) }
            }
        }
    }

    //remove
    override fun dequeue(): E? {
        while (true) {
            val curHead = head

            val curHeadNext = curHead.value.next ?: throw Exception()
            if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                return curHeadNext.value as E
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
