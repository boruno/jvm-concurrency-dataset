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
            val nodeToEnqueue = Node(x)
            val curTail = tail.value

            if (curTail.next.compareAndSet(null, nodeToEnqueue)) {
                if (tail.compareAndSet(curTail, nodeToEnqueue)) {
                    return
                }
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
            val headNode = head
            val headNextNode = headNode.value.next

            if (headNextNode.value == null) {
                return null
            }

            if (headNode.compareAndSet(head.value, headNextNode.value!!)) {
                return headNextNode.value!!.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value == tail.value &&
                head.value.next.value == null &&
                tail.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}