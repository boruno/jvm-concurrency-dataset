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
        val newTail = Node(x)
        while (true) {
            val curTail = tail.value
            val next = curTail.next.value
            if (curTail == tail.value) {
                if (next == null) {
                    if (curTail.next.compareAndSet(null, newTail)) {
                        tail.compareAndSet(curTail, newTail)
                        return
                    }
                } else {
                    tail.compareAndSet(curTail, next)
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
            val curHead = head.value
            val curTail = tail.value
            val next = curHead.next.value

            if (isEmpty()) return null;

            if (curHead == head.value) {
                if (curHead == tail.value) {
                    if (next == null) return null
                    tail.compareAndSet(curTail, next)
                } else {
                    if (head.compareAndSet(curHead, next!!)) {
                        return next.x
                    }
                }
            }
        }
    }

    /**
     * Return true if the queue is empty, otherwise false
     */
    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}