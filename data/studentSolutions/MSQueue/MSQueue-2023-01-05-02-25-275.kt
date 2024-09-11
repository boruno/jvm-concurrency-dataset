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
        while ( true ) {
            val next_tail = Node<E>(x)
            val cur_tail = tail.value
            if ( cur_tail.next.compareAndSet( null, next_tail ) ) {
                if ( tail.compareAndSet( cur_tail, next_tail ) ) {
                    return
                }
            } else {
                if ( tail.compareAndSet( cur_tail, cur_tail.next.value!! ) ) {
//                    return
                }
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while ( true ) {
            val cur_head = head.value
            if ( isEmpty() ) {
                return null
            }
            if ( cur_head.next.value == null ) {
                return null
            } else {
                val cur_head_next = cur_head.next.value
                if ( head.compareAndSet( cur_head, cur_head_next!! ) ) {
                    return cur_head_next.x
                }
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