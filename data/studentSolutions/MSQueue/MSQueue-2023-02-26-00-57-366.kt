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
        while (true) {
            val node = Node<E>(x)
            val cur_tail = tail.value
            if (cur_tail.next.compareAndSet(null, node )) {
                tail.compareAndSet(cur_tail, node)
                return
            } else {
                tail.compareAndSet(cur_tail, cur_tail.next.value as Node<E>)
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
            val cur_head = head.value
            val cur_head_next = cur_head.next.value
            if (cur_head_next == null) {
                return null
            }
            if (head.compareAndSet(cur_head, cur_head_next)) {
                return cur_head_next.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value == tail.value
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}