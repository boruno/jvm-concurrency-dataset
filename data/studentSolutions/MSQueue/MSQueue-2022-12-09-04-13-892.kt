//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        val newNode = Node(x)
        while (true) {
            val oldTail = tail.value
            val oldNext = oldTail.next.value
            if (tail.value == oldTail) {
                if (oldNext == null) {
                    if (oldTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(oldTail, newNode)
                        return
                    }
                } else {
                    tail.compareAndSet(oldTail, oldNext)
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
            val oldHead = head.value
            val oldTail = tail.value
            val oldHeadNext = oldHead.next.value
            if (oldHead == head.value) {
                if (oldHead == oldTail) {
                    if (oldHeadNext == null) {
                        return null
                    }
                    tail.compareAndSet(oldTail, oldHeadNext)
                } else {
                    val ret = oldHead.x
                    if (head.compareAndSet(oldHead, oldHeadNext!!)) {
                        return ret
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.compareAndSet(null, null)
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}