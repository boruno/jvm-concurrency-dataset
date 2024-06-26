package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>
    private val dummy = Node<E>(null)

    init {
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        val node = Node(x)
        val curTail = tail.value
        if (curTail.next.compareAndSet(dummy, node)) {
            tail.compareAndSet(curTail, node)
            return
        } else {
            val tmp = curTail.next.value ?: dummy
            tail.compareAndSet(curTail, tmp)
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
            val curHeadNext = curHead.next.value
            if (curHeadNext == dummy || curHeadNext == null) {
                return null
            }
            if (head.compareAndSet(curHead, curHeadNext)) {
                return curHead.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == dummy
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}