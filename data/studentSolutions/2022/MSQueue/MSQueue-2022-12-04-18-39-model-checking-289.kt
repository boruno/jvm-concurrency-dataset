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
            val t = tail.value
            if (t.next.compareAndSet(null, node)) {
                tail.compareAndSet(t, node)
                return
            } else {
                t.next.value?.let { tail.compareAndSet(t, it) }
            }
        }
        TODO("implement me")
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val h = head.value
            val t = tail.value
            val next = head.value.next.value


            if (h == t) {
                if (next == null) {
                    return null
                }
                tail.compareAndSet(t, next)
            } else {
                if (next?.let { head.compareAndSet(h, it) } == true) {
                    return next.x
                }
            }

        }
        TODO("implement me")
    }

    fun isEmpty(): Boolean {
        val h = head.value
        val t = tail.value
        val next = head.value.next.value
        if (h == t) {
            if (next == null) {
                return false
            }
        }
        return true
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}