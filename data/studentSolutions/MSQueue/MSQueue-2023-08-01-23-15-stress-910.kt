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
        while(true) {
            val newNode = Node(element)

            val currentTail = tail.value

            if(tail.value.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)
//                currentTail.next = atomic(newNode)
                return
            } else {
                // helping
//                tail.compareAndSet(currentTail, currentTail.next.value)
            }

        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value ?: error("error")

            if (head.compareAndSet(currentHead, currentHeadNext)){
                return currentHeadNext.element
            }
        }
    }


    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
