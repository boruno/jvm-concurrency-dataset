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

        do {
            val curTail = tail.value
            val tailNext = curTail.next
            val next = curTail.next.value

            if (!tailNext.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, next!!)
            }

        } while (tail.compareAndSet(curTail, newTail))

    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        do {
            val curHead = head.value
            val headNext = curHead.next.value
        } while (head.compareAndSet(curHead, headNext!!))

        return head.value.next.value?.x
    }

    fun isEmpty(): Boolean {
        if (head.value == tail.value) {
            return true
        }
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}