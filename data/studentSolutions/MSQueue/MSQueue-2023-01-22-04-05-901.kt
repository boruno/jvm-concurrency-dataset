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
        val newTail: Any? = Node<Any?>(x)
        while (true) {
            val valTail: Node<E> = tail.value
            if (valTail.next.compareAndSet(null, newTail as Node<E>?)) {
                if (newTail != null) {
                    tail.compareAndSet(valTail, newTail)
                }
                return
            }
            valTail.next.value?.let { tail.compareAndSet(valTail, it) }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): Any? {
        while (true) {
            val curHead: Node<E> = head.value
            val curTail: Node<E> = tail.value
            val nextHead = curHead.next.value
            val nextTail = curTail.next.value
            if (nextHead == null) return Integer.MIN_VALUE
            if (head.compareAndSet(curHead as Node<E>, nextHead as Node<E>)) return nextHead.x1
            if (curHead == curTail) tail.compareAndSet(curTail, nextTail as Node<E>)
        }
    }

    fun isEmpty(): Boolean {
        val nextHead = head.value.next.value ?: return true
        return false
    }
}

private class Node<E>(val x: E?) {
    val x1 = x
    val next = atomic<Node<E>?>(null)
}