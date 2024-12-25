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
        while ( true ) {
            var next_tail = Node<E>(x)
            var cur_tail = tail
            if ( cur_tail.value.next.compareAndSet( null, next_tail ) ) {
                tail.compareAndSet( cur_tail.value, next_tail )
                return
            } else {
                tail.compareAndSet( cur_tail.value, cur_tail.value.next.value!! )
                 return
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
            var cur_head = head
            if ( isEmpty() )
                return null
            if ( cur_head.value.equals( null ) ) {
                return null
            } else {
                var cur_head_next = cur_head.value.next
                if ( cur_head_next.value == null ) {
                    return null
                }
                head.compareAndSet( Node<E>( null ), cur_head_next.value!! )
                if ( head.compareAndSet( cur_head.value, cur_head_next.value!! ) ) {
                    return cur_head.value.x
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