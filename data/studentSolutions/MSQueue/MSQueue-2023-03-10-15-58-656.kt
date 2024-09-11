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
        val newTail: Node<E> = Node(x)
        while (true) {
            val curTail: Node<E> = tail.value
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
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
            val curHead: Node<E> = head.value
            if (curHead == tail.value) {
                return null
            }
            val next = curHead.next.value
            if (head.compareAndSet(curHead, next!!)) {
                return next.x
            }
        }
    }

    fun isEmpty(): Boolean {
//        return head.value == tail.value
        return true
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}