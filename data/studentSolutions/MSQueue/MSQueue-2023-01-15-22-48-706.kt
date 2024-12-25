//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null, null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        val newTail = Node(x, null)
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                return
            }
            curTail.next.value?.let { tail.compareAndSet(curTail, it) }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value ?: return null
//            if (tail.value === currentHead) {
//                return null
//            }
            if (head.compareAndSet(currentHead, currentHeadNext)) {
                return currentHeadNext.x
            }
        }
    }

    fun isEmpty(): Boolean {
        head.value.next.value ?: return false
        return true
    }
}

private class Node<E>(val x: E?, node: Node<E>?) {
    val next = atomic(node)
}