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
        var newTail = Node(x);
        while (true) {
            var curTail = tail.value
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail);
                return;
            } else {
                curTail.next.value?.let { tail.compareAndSet(curTail, it) };
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
            val curHead: Node<E> = head.value
            val curTail: Node<E> = tail.value
            val curHeadNext = curHead.next.value
            val curTailNext = curTail.next.value
            if (curHeadNext == null) return null
            if (head.compareAndSet(curHead, curHeadNext)) return curHeadNext.x
            if (curTailNext != null) tail.compareAndSet(curTail, curTailNext)
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value != null;
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null);
    val cur = x;
}