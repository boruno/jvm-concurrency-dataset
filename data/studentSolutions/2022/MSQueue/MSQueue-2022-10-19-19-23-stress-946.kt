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
        while (true) {
            val newNode = Node(x)
            val currentTail = tail.value
            val currentTailNext = tail.value.next

            if (currentTailNext.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)
                return
            } else {
                tail.compareAndSet(currentTail, currentTailNext.value!!)
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
            var currentHeadNext = currentHead.next.value

            if (isEmpty())
                return null

            if (head.compareAndSet(currentHead, currentHeadNext!!)) {
                return currentHeadNext.x
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