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
        val newTail = Node(x)
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newTail)) { // failed to do CAS, means that another thread enqueued some element
                tail.compareAndSet(curTail, newTail)
                return
            } else {
                curTail.next.value?.let { tail.compareAndSet(curTail, it) }
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
            if (isEmpty()) {
                return null
            }
            val curHead = head.value
            if (curHead.next.value?.let { head.compareAndSet(curHead, it) } == true) {
                return curHead.x
            }
        }
    }

    fun isEmpty(): Boolean {
        // in this case CAS-2 would be the right solution but let's assume we don't know it yet
        return tail.value == head.value
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}