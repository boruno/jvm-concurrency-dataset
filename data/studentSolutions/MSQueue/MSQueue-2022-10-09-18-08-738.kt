package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * @author Viktor Panasyuk
 */
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
        val newNode = Node(x)
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                return
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
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, curHeadNext)) {
                return curHeadNext.value
            }
        }
    }

    /**
     * Returns `true` if queue is empty.
     */
    fun isEmpty(): Boolean {
        return head.compareAndSet(tail.value, head.value)
    }
}

private class Node<E>(val value: E?) {
    val next = atomic<Node<E>?>(null)
}