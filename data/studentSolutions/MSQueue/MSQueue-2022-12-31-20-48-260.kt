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
        val newNode = Node(x)
        while (true) {
            val currentTail = tail.value
            if (currentTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)
                return
            } else {
                currentTail.next.value?.let { tail.compareAndSet(currentTail, it) }
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
            if (currentHead == currentTail) {
                nextHead ?: return null
                tail.compareAndSet(currentTail, nextHead)
            } else {
                val aboba = nextHead ?: Node<E>(null)
                if (head.compareAndSet(currentHead, aboba)) {
                    return currentHead.x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.x == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}
