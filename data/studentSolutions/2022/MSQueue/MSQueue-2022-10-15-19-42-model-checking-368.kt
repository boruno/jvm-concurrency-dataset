package mpp.msqueue

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
            val currentTail = tail.value
            val nextTail = currentTail.next.value
            if (currentTail == tail.value) {
                if (nextTail != null) {
                    tail.compareAndSet(currentTail, nextTail)
                } else {
                    if (currentTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(currentTail, newNode)
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
            val currentHead = head.value
            val currentTail = tail.value
            val next = currentHead.next.value
            if (currentHead == head.value) {
                if (currentHead == currentTail) {
                    if (next == null) {
                        return null
                    } else {
                        // help to producer
                        tail.compareAndSet(currentTail, next)
                    }
                } else {
                    if (head.compareAndSet(currentHead, next!!)) {
                        val item = next.x
                        next.x = null // help to gc
                        return item
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.x == null
    }
}

private class Node<E>(var x: E?) {
    val next = atomic<Node<E>?>(null)
}