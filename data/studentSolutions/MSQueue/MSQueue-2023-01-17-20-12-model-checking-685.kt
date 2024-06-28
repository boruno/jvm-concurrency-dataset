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
        while (true) {
            val t = tail.value
            val newTail = Node(x)
            if (t.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(t, newTail)
                return
            } else {
                tail.compareAndSet(t, t.next.value!!)
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
            val h = head.value
            val next = h.next.value ?: return null
            if (head.compareAndSet(h, next)) {
                return next.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value != null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}