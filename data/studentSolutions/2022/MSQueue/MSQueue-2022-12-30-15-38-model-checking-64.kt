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
            val node = Node(x);
            val curTail = tail.value;
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node);
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!);
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
            val curHead = head.value;
            val curHeadNext = curHead.next.value;
            if (curHeadNext === null) {
                return null;
            }
            if (head.compareAndSet(curHead, curHeadNext)) {
                return curHeadNext.x
            }
        }
    }

    fun isEmpty(): Boolean {
        val curTail = tail.value;
        val curHead = head.value;
        return curTail === curHead;
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}