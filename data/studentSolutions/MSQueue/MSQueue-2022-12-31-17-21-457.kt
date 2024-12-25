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
            val tail = this.tail.value
            val next = tail.next.value
            if (tail == this.tail.value) {
                if (next == null) {
                    if (tail.next.compareAndSet(next, node)) {
                        this.tail.compareAndSet(tail, node)
                        break
                    }
                } else {
                    this.tail.compareAndSet(tail, next)
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
        val head = this.head.value
        val tail = this.tail.value
        val next = head.next.value
        while (true) {
            if (head == tail) {
                if (next == null) {
                    return null
                }
                this.tail.compareAndSet(tail, next)
            } else {
                val x = next?.x
                if (this.head.compareAndSet(head, next!!)) {
                    return x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value == tail.value
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}
