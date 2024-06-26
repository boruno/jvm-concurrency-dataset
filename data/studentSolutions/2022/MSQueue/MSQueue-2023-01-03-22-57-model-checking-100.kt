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
        val newNode = Node(x)
        while (true) {
            val currentTail = this.tail.value
            if (currentTail.next.compareAndSet(null, newNode)) {
                this.tail.compareAndSet(currentTail, newNode)
                return
            } else {
                this.tail.compareAndSet(currentTail, currentTail.next.value!!)
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
            if (currentHead.value == this.tail.value) {
                val next = this.tail.value.next.value ?: return null
                tail.compareAndSet(this.tail.value, next)
            }
            if (this.head.compareAndSet(currentHead.value, currentHead.value.next.value!!)) {
                return currentHead.value.next.value!!.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.x == null && head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Node<*>

        if (x != other.x) return false
        if (next.value != other.next.value) return false

        return true
    }

    override fun hashCode(): Int {
        var result = x?.hashCode() ?: 0
        result = 31 * result + next.hashCode()
        return result
    }
}