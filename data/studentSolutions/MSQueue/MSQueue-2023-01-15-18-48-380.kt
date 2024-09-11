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
        for (i in 1..1000) {
            val ourTail = tail.value
            if (ourTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(ourTail, newTail)
            } else {
                tail.compareAndSet(ourTail, ourTail.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        for (i in 1..1000) {
            val ourHead = head.value
            val ourTail = tail.value
            val ourHeadNext = ourHead.next.value
            if (ourTail == ourHead) {
                if (ourHeadNext == null) return null
                tail.compareAndSet(ourTail, ourHeadNext)
            } else {
                val result = ourHeadNext?.x
                if (head.compareAndSet(ourHead, ourHeadNext!!)) {
                    return result
                }
            }
        }
        return null
    }

    fun isEmpty(): Boolean {
        val ourHead = head.value
        val ourTail = tail.value
        val ourHeadNext = ourHead.next.value
        return ourTail == ourHead && ourHeadNext?.x == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}