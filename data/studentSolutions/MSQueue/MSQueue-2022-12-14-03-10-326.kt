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
            val node = Node(x)
            val tailNode = tail.value
            if (tailNode.next.compareAndSet(null, node)) {
                tail.compareAndSet(tailNode, node)
                return
            } else {
                val tailNodeNext = tailNode.next.value
                if (tailNodeNext != null) {
                    tail.compareAndSet(tailNode, tailNodeNext)
                } else {
                    throw IllegalStateException()
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
        while (true) {
            val headNode = head.value
            val headNodeNext = headNode.next.value
            if (headNodeNext == null) {
                return null
            } else if (head.compareAndSet(headNode, headNodeNext)) {
                return headNodeNext.x
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