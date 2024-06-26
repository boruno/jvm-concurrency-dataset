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
        val newEl = Node(x)
        while (true) {
            val oldEl = tail.value
            if (oldEl.next.compareAndSet(null, newEl)) {
                tail.compareAndSet(oldEl, newEl)
                return
            } else {
                tail.compareAndSet(oldEl, oldEl.next.value!!)
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
            val headEl = head.value
            val nextEl = headEl.next.value
            val tailEl = tail.value
            if (headEl == tailEl) {
                if (nextEl == null) return null
                tail.compareAndSet(tailEl, nextEl)
            } else if (head.compareAndSet(headEl, nextEl!!)) {
                return nextEl.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}