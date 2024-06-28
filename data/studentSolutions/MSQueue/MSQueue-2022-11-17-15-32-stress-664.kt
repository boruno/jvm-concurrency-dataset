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
            var newNode = Node(x)
            var currentTail = tail.value
            if (currentTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, currentTail.next.value!!)
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
            var currentHead = head
            var currentHeadNext = currentHead.value.next
            if (currentHeadNext.value == null) {
                return null
            }
            if (head.compareAndSet(currentHead.value, currentHeadNext.value!!)) {
                return currentHeadNext.value!!.x
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