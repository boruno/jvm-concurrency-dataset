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
            val currentTail = tail.value
            val tailNext = currentTail.next.value
            if (currentTail == tail.value) {
                if (tailNext == null) {
                    if (currentTail.next.compareAndSet(tailNext, node)) {
                        tail.compareAndSet(currentTail, node)
                        return
                    }
                } else {
                    tail.compareAndSet(currentTail, tailNext)
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
            val currentHead = head.value
            val next = currentHead.next.value
            if (currentHead == head.value) {
                if (next == null) {
                    return null
                } else {
                    if (head.value.next.compareAndSet(next, next.next.value)) {
                        return next.x
                    }
                }
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