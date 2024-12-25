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
            val currentTail = tail.value
            if (currentTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(currentTail, node)
                return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
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
            val currentTail = tail.value
            val nextHead = currentHead.next.value
            if (currentHead === currentTail) {
                if (isEmpty()) return null
                tail.compareAndSet(currentTail, nextHead!!)
            } else {
                if (head.compareAndSet(currentHead, nextHead!!)) return nextHead.x
            }
        }
    }

    fun isEmpty(): Boolean {
        val head = head.value
        val tail = tail.value
        val nextHead = head.next.value
        return head === tail && nextHead == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}