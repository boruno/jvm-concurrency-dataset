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

    //add
    override fun enqueue(element: E) {
        while (true) {
            val newNode = Node(element)
            val currentTail = tail
            val value = currentTail.value
            val next = value.next
            if (next.compareAndSet(null, newNode)) {
                tail.compareAndSet(value, newNode)
                return
            } else {
                tail.compareAndSet(value, next.value!!)
            }
        }
    }

    //remove
    override fun dequeue(): E? {
        while (true) {
            if (tail.compareAndSet(head.value, tail.value)) return null
            val curHead = head
            val curHeadNext = curHead.value.next ?: throw Exception()
            val update = curHeadNext.value ?: return null
            if (head.compareAndSet(curHead.value, update)) {
                return update.element as E
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
