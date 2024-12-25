//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    private val dummy = Node<E>(null)

    private val n = atomic(0);

    init {
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        while (true) {
            val node = Node(x);
            val currentTail = tail.value;
            if (currentTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(currentTail, node)
                n.incrementAndGet();
                return;
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
            val currentHead = head.value;
            val currentHeadNext = currentHead.next;
            if (currentHeadNext.value == null) {
                return null;
            }
            if (head.compareAndSet(currentHead, currentHeadNext.value!!)) {
                n.decrementAndGet();
                return currentHeadNext.value!!.x;
            }
        }
    }

    fun isEmpty(): Boolean {
        return n.value == 0
        //return (head.value == tail.value) && tail.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}