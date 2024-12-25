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
        val node = Node(x)
        while (true) {
            val currentTail = this.tail.value
            if (currentTail.next.compareAndSet(null, node)) {
                this.tail.compareAndSet(currentTail, node)
                return
            }
            val next = currentTail.next.value
            if (next != null) {
                this.tail.compareAndSet(currentTail, next)
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
            val head = this.head.value
            val next = head.next.value
            if (next?.x == null) {
                return null
            }
            if (this.head.compareAndSet(head, next)) {
                return head.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return this.head.value.next.value?.x == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}