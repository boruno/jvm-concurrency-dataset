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
            val next = tail.value.next
            if(next.value == null)
                if(next.compareAndSet(next.value, node)) break
            else
                tail.compareAndSet(tail.value, next.value!!)
        }
        tail.compareAndSet(tail.value, node)
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while(true) {
            val next = head.value.next
            if(tail.value == head.value) {
                if(isEmpty())
                    return null
                tail.compareAndSet(tail.value, next.value!!)
            } else {
                val x = next.value?.x
                if(head.compareAndSet(head.value, next.value!!))
                    return x
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