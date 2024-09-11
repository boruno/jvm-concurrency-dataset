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
            val currentTail = this.tail;
            if (currentTail.value.next.compareAndSet(null, newNode)) {
                this.tail.compareAndSet(currentTail.value, newNode)
                return
            } else {
                this.tail.compareAndSet(currentTail.value, currentTail.value.next.value!!)
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
            val currentHead = this.head
            val currentHeadNext = currentHead.value.next.value ?: return null
            if (this.head.compareAndSet(currentHead.value, currentHeadNext)) {
                return currentHeadNext.x
            }
        }
    }

    fun isEmpty(): Boolean {
        val currentHead = this.head
        return currentHead.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}