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
            val tail = tail.value
            val next = tail.next.value
            if (tail == this.tail.value) {
                if (next == null) {
                    if (tail.next.compareAndSet(next, node)) {
                        break
                    }
                } else {
                    this.tail.compareAndSet(tail, next)
                }
            }
        }
        this.tail.compareAndSet(tail.value, node)
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        var head: Node<E> = this.head.value
        while (true) {
            head = this.head.value
            val tail = this.tail.value
            val next = head.next
            if (head == this.head.value) {
                if (isEmpty()) {
                    if (next.value == null) {
                        return null
                    }
                    this.tail.compareAndSet(tail, next.value!!)
                } else {
                    if (this.head.compareAndSet(head, next.value!!)) {
                        break
                    }
                }
            }
        }
        return head.x
    }

    fun isEmpty(): Boolean = head.value == tail.value
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}