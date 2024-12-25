//package day1

import kotlinx.atomicfu.*
import java.util.EmptyStackException

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
            // try updating the last element
            val lastElement = tail.value
            val nextLastElement = Node(element)

            if (lastElement.next.compareAndSet(null, nextLastElement)) {
                // we successfully updated last element,
                // now it's time to move the tail
                if (tail.compareAndSet(lastElement, nextLastElement)) {
                    return // we have moved the tail
                } else {
                    return // tail was moved for us
                }
            } else {
                // we can't insert the last element, because new element was inserted
                // let's move the tail then
                val next = lastElement.next.value
                    ?: continue // fixme: this should not be null?

                tail.compareAndSet(lastElement, next)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val nextHead = currentHead.next.value
                ?: return null // queue is empty

            if (head.compareAndSet(currentHead, nextHead)) {
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
