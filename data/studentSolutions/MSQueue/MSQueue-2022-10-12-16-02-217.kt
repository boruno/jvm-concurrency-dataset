//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

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
        tail.loop { cur ->
            if (tail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(cur, node)
                return
            } else {
                tail.compareAndSet(cur, cur.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        head.loop { cur ->
            if (cur.next.value == null)
                return null
            if (head.compareAndSet(cur, cur.next.value!!))
                return cur.x
        }
    }

    fun isEmpty(): Boolean {
        if (head.value.next.compareAndSet(null, null))
            return true
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}