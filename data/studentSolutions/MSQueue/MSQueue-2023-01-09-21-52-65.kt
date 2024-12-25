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
            val curTail = tail.value;
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                return;
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
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
            val headN = head.value
            val tailN = tail.value
            val nextH = head.value.next.value
            if (headN == head.value) {
                if (headN == tailN) {
                    if (nextH == null) {
                        return null
                    }
                    tail.compareAndSet(tailN, nextH!!)
                } else {
                    if (nextH != null) {
                        head.compareAndSet(headN, nextH)
                        return nextH.x;
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        if (tail.value.x == null) {
            return true;
        }
        return false;
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}