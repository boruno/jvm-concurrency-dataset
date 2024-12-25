//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    private val nullNode = Node<E>(null)

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        val node = Node(x)

        while (true) {
            val curTail = tail.value

            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
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
            val curHead = head.value
            val curHeadNext = curHead.next

            if (curHeadNext.value === nullNode) {
                throw Exception("Empty queue")
            }

            if (head.compareAndSet(curHead, curHeadNext.value!!)) {
                return curHeadNext.value?.x
            }
        }
    }

    fun isEmpty(): Boolean = head.value === tail.value
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}
