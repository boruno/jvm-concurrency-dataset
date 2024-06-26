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

    // thx. MPP 2022 4.1. Classic Stack and Queue Algorithms.pdf

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        while (true) {
            val currNode = Node(x)
            val currTail = tail.value

            if (currTail.next.compareAndSet(null, currNode)) {
                tail.compareAndSet(currTail, currNode)
                return
            } else {
                val currTailNext = currTail.next.value!!
                //tail.compareAndSet(currTail, currTailNext)
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
            val currHead = head.value
            val currHeadNext = currHead.next.value ?: return null

            if (head.compareAndSet(currHead, currHeadNext)) {
                return currHeadNext.x
            }
        }
    }

    // Check head
    fun isEmpty(): Boolean = head.value.next.compareAndSet(null, null)
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}