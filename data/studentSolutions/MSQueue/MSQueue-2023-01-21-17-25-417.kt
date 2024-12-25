//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * @author Pologov Nikita
 */

class MSQueue<E> {
    public val qHead: AtomicRef<Node<E>>
    public val qTail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        qHead = atomic(dummy)
        qTail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        val node = Node(x)

        while (true) {
            val tail = qTail.value
            val next = tail.next.value
            if (tail == qTail.value) {
                if (next == null) {
                    if (tail.next.compareAndSet(null, node)) {
                        qTail.compareAndSet(tail, node)
                        return
                    }
                } else {
                    qTail.compareAndSet(tail, next)
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
            val tail =  qTail.value
            val next = head.next.value
            if (head == qHead.value) {
                if (head == tail) {
                    if (next == null) {
                        return null
                    }
                    qTail.compareAndSet(tail, next)
                } else {
                    val result = next!!.x
                    if (qHead.compareAndSet(head, next)) {
                        return result
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        val res = qHead.value.x == null
        return  res
    }
}

class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}