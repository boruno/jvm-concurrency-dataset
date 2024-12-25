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
            val tailNode = tail.value
            val newNode = Node(x)
            if (tailNode.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(tailNode, newNode)
                return
            } else {
                tail.compareAndSet(tailNode, newNode)
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
            val headNode = head.value
            val nextNode = headNode.next.value ?: return null
            if (head.compareAndSet(headNode, nextNode))
                return nextNode.x
        }
    }

    fun isEmpty(): Boolean {
        val headNode = head.value
        return headNode.x == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}