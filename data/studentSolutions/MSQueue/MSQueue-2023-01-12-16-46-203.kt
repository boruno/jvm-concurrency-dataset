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
            val oldTailNext = oldTail.next.value
            if (oldTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(oldTail, newTail)
                return
            } else {
                tail.compareAndSet(oldTail, oldTailNext!!)
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
            val oldHeadNext = oldHead.next.value ?: return null
            if (head.compareAndSet(oldHead, oldHeadNext))
                return oldHeadNext.x
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}