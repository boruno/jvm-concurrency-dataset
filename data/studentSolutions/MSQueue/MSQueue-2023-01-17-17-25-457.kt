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
            val nodeToEnqueue = Node(x)
            val curTail = tail.value

            if (curTail.next.compareAndSet(null, nodeToEnqueue)) {
                tail.compareAndSet(curTail, nodeToEnqueue)
                return
            } else {
                val curTailNext = curTail.next.value ?: continue
                tail.compareAndSet(curTail, curTailNext)
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
            val headNextNode = headNode.next.value ?: return null

            if (head.compareAndSet(headNode, headNextNode)) {
                return headNextNode.x
            }
        }
    }

    fun isEmpty(): Boolean {
        val curHead = head.value
        val curTail = tail.value
        val nextHead = curHead.next.value

        if (curHead == curTail) {
            if (nextHead == null) {
                return true
            }
        }
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}