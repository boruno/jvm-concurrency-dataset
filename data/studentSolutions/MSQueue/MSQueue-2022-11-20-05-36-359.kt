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
            val cur_tail = tail.value
            val node = Node(x)
            if (cur_tail.next.compareAndSet(null, node)) {
                tail.compareAndSet(cur_tail, node)
                return
            } else {
                tail.compareAndSet(cur_tail, cur_tail.next.value!!)
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
            val cur_head_next = cur_head.next
            if (cur_head_next.value == null) {
                return null
            } else {
                if (head.compareAndSet(cur_head, cur_head_next.value!!)) {
                    return cur_head_next.value!!.x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return tail.value.next.compareAndSet(null, null)
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}