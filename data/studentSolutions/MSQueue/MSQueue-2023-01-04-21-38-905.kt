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
            val currentTail = this.tail.value
            val currentTailNext = currentTail.next.value
            if (currentTail == this.tail.value) {
                if (currentTailNext == null) {
                    if (currentTail.next.compareAndSet(null, newNode)) {
                        this.tail.compareAndSet(currentTail, newNode)
                        return
                    }
                } else {
                    this.tail.compareAndSet(currentTail, currentTailNext)
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
            val currentHead = this.head.value
            val currentTail = this.tail.value
            val currentHeadNext = currentHead.next.value
            if (currentHead == this.head.value) {
                if (currentHead == currentTail) {
                    if (currentHeadNext == null) {
                        return null
                    } else {
                        this.tail.compareAndSet(currentTail, currentHeadNext)
                    }
                } else {
                    if (this.head.compareAndSet(currentHead, currentHeadNext!!)) {
                        return currentHeadNext.x
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        while (true) {
            val currentHead = this.head
            val currentTail = this.tail
            if (currentHead.value == currentTail.value) {
                val headNext = currentHead.value.next.value ?: return true
                this.head.compareAndSet(currentHead.value, headNext)
            } else {
                return false
            }
        }
    }

    private class Node<E>(val x: E?) {
        val next = atomic<Node<E>?>(null)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Node<*>

            if (x != other.x) return false

            return true
        }

        override fun hashCode(): Int {
            var result = x?.hashCode() ?: 0
            result = 31 * result + next.hashCode()
            return result
        }
    }
}