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
        val node = Node(x)
        while(true) {
            val curtail = tail.value
            if(curtail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curtail, node)
                break
            } else {
                tail.compareAndSet(curtail, curtail.next.value!!)
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
            val curhead = head
            val next = curhead.value.next.value
            if(isEmpty())
                return null
            if(head.compareAndSet(curhead.value, next!!))
                return next.x

        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}