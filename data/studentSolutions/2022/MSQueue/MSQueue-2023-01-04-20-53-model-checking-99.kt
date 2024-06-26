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
        node.next.value = null
        while (true) {
            val t = tail.value
            val next = t.next.value
            if (next == null) {
                if (t.next.compareAndSet(null, node)) {
                    tail.compareAndSet(t, node)
                    return
                }
            } else {
                tail.compareAndSet(t, next)
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
            val next = head.value.next.value
            if (isEmpty()) {
                if (next == null) {
                    return null
                } else {
                    tail.compareAndSet(head.value, next)
                }
            } else {
                val result = next
                if (next?.let { head.compareAndSet(head.value, it) } == true) {
                    return result!!.x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value === tail.value
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}