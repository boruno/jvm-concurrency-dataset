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
            val head = this.head.value;
            val tail = this.tail.value;
            val next = this.head.value.next;
            if (head == this.head.value) {
                if (head == tail) {
                    if (isEmpty()) {
                        return null;
                    }
                    next.value?.let { this.tail.compareAndSet(tail, it) }
                    this.tail.value.next.compareAndSet(null, next.value)
                } else {
                    if (next.value?.let { this.head.compareAndSet(head, it) } == true) {
                        return next.value!!.x;
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