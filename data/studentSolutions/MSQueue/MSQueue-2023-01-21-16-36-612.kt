//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * @author Pologov Nikita
 */

class MSQueue<E> {
    private val qHead: AtomicRef<Node<E>>
    private val qTail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        qHead = atomic(dummy)
        qTail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        val node = Node(x);
        while (true) {
            val tail = qTail.value
            val next = tail.next.value
            if (tail == qTail.value) {
                if (next != null) {
                    qTail.compareAndSet(tail, next)
                } else {
                    if (tail.next.compareAndSet(null, node)) {
                        qTail.compareAndSet(tail, node)
                        return
                    }
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val head = qHead.value
            val tail = qTail.value
            val next = head.next.value
            if (qHead.value == head) {
                if (next == null) {
                    return null
                }
                if (head == tail) {
                    qTail.compareAndSet(tail, tail.next.value!!)
                } else {
                    if (qHead.compareAndSet(head, next)) {
                        return next.x
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return qTail.value == qHead.value
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}