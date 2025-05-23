//package mpp.msqueue

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
        val node = Node(x)
        val curTail = tail
        if (curTail.value.next.compareAndSet(null, node)) {
            tail.compareAndSet(curTail.value, node)
            return
        } else {
            curTail.value.next.value?.let { tail.compareAndSet(curTail.value, it) }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty()) {
                return null
            }
            val curHead = head
            val curHeadNext = curHead.value.next
            curHeadNext.value?.let {
                if (head.compareAndSet(curHead.value, it)) {
                    return curHeadNext.value!!.x
                }
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