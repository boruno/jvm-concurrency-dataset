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
            val tail = tail.value
            val curTail = tail.next
            if (curTail.compareAndSet(null, newTail)) {
                break
            }
        }

        while (true) {
            val tail = tail.value
            val nextTail = tail.next.value!!
            if (this.tail.compareAndSet(tail, nextTail)) {
                break
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
            val head =  head.value
            if (isEmpty()) {
                return null
            }
            val newHead = head.next.value!!
            if (this.head.compareAndSet(head, newHead)) {
                return newHead.x
            }
        }
    }

    fun isEmpty(): Boolean = head.value == tail.value
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}