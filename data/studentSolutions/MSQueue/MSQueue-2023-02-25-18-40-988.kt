package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

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
        val newNode = Node(x)
        tail.loop {
            if (tail.value.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(it, newNode)
                return
            } else {
              tail.compareAndSet(it, it.next.value!!)
            }
        }

    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        head.loop {
            if (it.next.value == null) {
                return null
            }
            if (head.compareAndSet(it, it.next.value!!)) {
                return it.x
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