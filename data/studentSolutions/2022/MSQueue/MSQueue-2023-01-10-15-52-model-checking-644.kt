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
        while (true) {
            val currNode = Node(x)
            val currTail = tail.value

            if (currTail.next.compareAndSet(null, currNode)) {
                tail.compareAndSet(currTail, currNode)
                return
            } else {
                val currTailNex = currTail.next.value!!
                tail.compareAndSet(currTail, currTailNex)
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

    fun isEmpty(): Boolean {
        return  head.value.x == null && tail.value.x == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}