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
            val newNode = Node(x);
            var curTail = tail.value;
            var curNext = curTail.next;
            if (curTail.next.compareAndSet(null, newNode)) {
                if (tail.compareAndSet(curTail, newNode)) {
                    return
                } else {
                    tail.compareAndSet(curTail, curTail.next.value as Node<E>);
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
            var curHead = head.value;
            var curNext = curHead.next.value;
            if (curNext == null) {
                return null
            } else {
                if (head.compareAndSet(curHead, curNext)) {
                    return curNext.x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        val curTail = tail;
        val curHead = head;
        while (true){
            if (curTail.value == tail.value && curHead.value == head.value)
            {
                return (curTail.value == curHead.value);
            }
        }
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}