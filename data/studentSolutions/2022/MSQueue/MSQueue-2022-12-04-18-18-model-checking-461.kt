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
            val t = tail
            val tNext = tail.value.next
            if (tNext.value == null) {
                if (tail.value.next.compareAndSet(null, node)) {
                    t.value.next.value?.let { tail.compareAndSet(t.value, it) }
                    return
                }
            } else {
                t.value.next.value?.let { tail.compareAndSet(t.value, it) }
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
        if (isEmpty()) {
            return null
        }
        while (true) {
            val h = head.value
            val t = tail.value
            val next = head.value.next
            if (h == head.value) {
                if (h == t) {
                    if (next.value == null) {
                        return null
                    }
                    tail.compareAndSet(t, next.value!!)
                } else {
                    if (head.compareAndSet(h, next.value!!)) {
                        return head.value.x
                    }
                }
            }
        }
        TODO("implement me")
    }

    fun isEmpty(): Boolean {
        val value = head.value
        if (value.x == null) {
            return true
        }
        return false
        TODO("implement me")
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}