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
            val newTail = Node(x)
            val curTail = tail.value
            val tailNext = curTail.next

            if (tailNext.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                return
            } else {
                tailNext.value?.let { tail.compareAndSet(curTail, it) }
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

            val headNext = curHead.next.value ?: return null
            if(head.compareAndSet(curHead,headNext)){
                return headNext.x
            }
        }
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