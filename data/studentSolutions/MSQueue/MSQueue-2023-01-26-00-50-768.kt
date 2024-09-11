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
            val valTail : Node<E> = tail.value
            if (valTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(valTail, newTail)
                return
            }
            tail.compareAndSet(valTail, valTail.next.value!!);
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
            val nextHead = curHead.next.value ?: return null
            val nextTail = curTail.next.value
            if (head.compareAndSet(curHead, nextHead))
                return nextHead.x
            if (curHead == curTail) {
                tail.compareAndSet(curTail, nextTail as Node<E>)
            }
        }
    }

    fun isEmpty(): Boolean {
        if (head.value == tail.value &&  head.value.next.value == null) return true
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}
