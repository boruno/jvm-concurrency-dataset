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
        val node = Node(element)
        val curTail = tail
        if (curTail.value.next.compareAndSet(null, node)) {
            tail.compareAndSet(curTail.value, node)
            return
        } else {
            tail.compareAndSet(curTail.value, curTail.value.next.value!!)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val curHeadNext = head.value.next

            if (curHeadNext.value == null) {
                throw (Exception("Empty queue"))
            }

            if (head.compareAndSet(curHead.value, curHeadNext.value!!)){
                return curHeadNext.value!!.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}