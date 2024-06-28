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
            val n = Node(element)
            val currentTail = tail.value
            if (currentTail.next.compareAndSet(null, n)) {
                tail.compareAndSet(currentTail, n)
                return
            } else {
                val actualTail = currentTail.next.value!!
                tail.compareAndSet(currentTail, actualTail)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentDummyHead =  head.value
            val currentHead = currentDummyHead.next.value
                ?: return null // queue is empty
            if (head.value.next.compareAndSet(currentHead, currentHead.next.value)) {
                tail.compareAndSet(currentHead, currentDummyHead)
                return currentHead.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}

//          tail
//            |
//Node@1 -> Node@2
//  |
//head
//
//null -> Node@2