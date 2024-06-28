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
        val newNode = Node(x)
        while (true) {
            val oldTail = tail.value
            if (oldTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(oldTail, newNode)
                return
            }
            tail.compareAndSet(oldTail, oldTail.next.value!!)
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val headValue = head.value
            val headNext = headValue.next.value ?: return null
            if (head.compareAndSet(headValue, headNext)) {
                return headNext.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return tail.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}