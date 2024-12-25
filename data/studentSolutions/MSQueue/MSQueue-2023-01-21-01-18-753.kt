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
        val newTail = Node(x)
        while (true) {
            val oldTail = tail.value
            if (oldTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(oldTail, newTail)
                return
            } else {
                tail.compareAndSet(oldTail, oldTail.next.value!!)
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
            val oldNext = oldHead.next.value
            if (head.value === oldHead) {
                if (oldHead === oldTail) {
                    if (oldNext == null) {
                        return null
                    }
                    tail.compareAndSet(oldTail, oldNext)
                } else {
                    if (head.compareAndSet(oldHead, oldNext!!)) {
                        return oldNext.x
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return true
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}