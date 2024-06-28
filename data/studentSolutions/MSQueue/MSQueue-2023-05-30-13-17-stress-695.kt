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
        while (true) {
            val node = Node(element)
            val curTail = tail
            val cTV = tail.value
            val cTVN = curTail.value.next
            if (curTail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(cTV, node)
                return
            } else {
                if (curTail.value.next.value != null) {
                    tail.compareAndSet(cTV, cTVN.value!!)
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val curHeadNext = curHead.value.next
            if (curHeadNext.value?.element == null) {
                throw Exception("Empty queue")
            }
            if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                return curHeadNext.value?.element
            }
        }

    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}





/*
override fun enqueue(element: E) {
    while (true) {
        val node = MSQueue.Node(element)
        val curTail = tail
        val next = curTail.value.next
        if (curTail.value.next.compareAndSet(null, node)) {
            tail.compareAndSet(curTail.value, node)
            return
        } else {
            if (curTail.value.next.value != null) {
                tail.compareAndSet(curTail.value, curTail.value.next.value!!)
            }
        }
    }
}
*/
