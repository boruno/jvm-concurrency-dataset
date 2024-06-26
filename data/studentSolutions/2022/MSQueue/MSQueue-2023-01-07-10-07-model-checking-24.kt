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
        val newNode = Node(x)
        while (true) {
            val tailTemp = tail.value
            if (tailTemp.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(tailTemp, newNode)
                break
            } else {
                tail.compareAndSet(tailTemp, tailTemp.next.value!!)
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
            val headTemp = head.value
            val tailTemp = tail.value
            val next = headTemp.next.value
            if (headTemp == tailTemp) {
                if (next == null) {
                    return null
                }
                tail.compareAndSet(tailTemp, next)
            } else {
                if (head.compareAndSet(headTemp, next!!)) {
                    return next.x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        TODO("implement me")
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}