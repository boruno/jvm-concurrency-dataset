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
        val newTail = Node(x)

        while (true) {
            val curTail = tail.value
            val tailNext = curTail.next
            val next = curTail.next.value

            if (tailNext.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                return
            } else
                tail.compareAndSet(curTail, next!!)
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
            val curTail = tail.value
            val headNext = curHead.next.value

            if (curHead == curTail) {
                tail.compareAndSet(curTail, curTail.next.value!!)
            } else if (head.compareAndSet(curHead, headNext!!)) {
                return headNext.x
            }
        }
    }

    fun isEmpty(): Boolean {
        if(head.value.next.value == null)
            return true

        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}