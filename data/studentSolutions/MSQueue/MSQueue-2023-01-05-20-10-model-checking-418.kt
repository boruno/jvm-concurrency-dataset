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
        while (true)
        {
            val node = Node<E>(x)
            val curTail = tail.value
            val nextTail = curTail.next
            if (nextTail.compareAndSet(null, node))
            {
                if (tail.compareAndSet(curTail, node))
                return
            }
            else
            {
                tail.compareAndSet(curTail, nextTail.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while(true)
        {
            val curHead = head
            val curHeadNext = head.value.next
            if (curHeadNext.value == null)
                return null
            if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                return curHeadNext.value!!.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value != null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}