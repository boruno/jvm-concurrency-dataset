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
        val newTail = Node(x)
        while (true) {
            val tempTailValue = tail.value
            if (tempTailValue.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(tempTailValue, newTail)
                break
            } else {
                tail.compareAndSet(tempTailValue, tempTailValue.next.value!!)
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
            val tempHead = head.value
            val tempTail = tail.value
            val next = tempHead.next.value
            if (tempHead == tempTail) {
                if (next == null) {
                    return null
                }
                tail.compareAndSet(tempTail, next)
            } else {
                if (head.compareAndSet(tempHead, next!!)) {
                    return next.x
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