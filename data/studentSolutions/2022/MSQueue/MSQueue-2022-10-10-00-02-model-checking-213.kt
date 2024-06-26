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
        val node = Node(x)
        while (true) {
            val last = tail.value
            val next = last.next.value
            if (last == tail.value) {
                if (next == null) {
                    if (last.next.compareAndSet(null, node)) {
                        tail.compareAndSet(last, node)
                        return
                    }
                } else {
                    tail.compareAndSet(last, next)
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
            val first = head.value
            val last = tail.value
            val next = first.next.value
            if (first == last) {
                if (next == null)
                    return null
                tail.compareAndSet(last, next)
            } else {
                if (next == null)
                    return null
                val value = next.x
                if (head.compareAndSet(first, next))
                    return value
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.x == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}