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
        while(true)
        {
            if (tail.value.next.compareAndSet(null, node))
            {
                val currentTail = tail.value
                tail.compareAndSet(currentTail, node)
                return
            }
            else
            {
                val currentTail = tail.value
                tail.compareAndSet(currentTail, tail.value.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while(true)
        {
            val currentNext = head.value.next.value ?: return null;
            val result = currentNext.x
            if (head.value.next.compareAndSet(currentNext, currentNext.next.value)) {
                tail.value = head.value
                return result
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null;
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}