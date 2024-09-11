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
        val newNode = Node<E>(x)
        while (true) {
            val curTail = tail.value
            val nextTail = curTail.next
            if (nextTail.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                return
            }
            else {
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
        while(true) {
            if (isEmpty())
                return null

            val curHead = head.value
            val curHeadNext = head.value.next
            val curTail = tail.value

            if (curTail.next.value != null)
                tail.compareAndSet(curTail, curTail.next.value!!)

            if (head.compareAndSet(curHead, curHeadNext.value!!)) {
                return curHeadNext.value!!.x
            }
        }
    }
    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}