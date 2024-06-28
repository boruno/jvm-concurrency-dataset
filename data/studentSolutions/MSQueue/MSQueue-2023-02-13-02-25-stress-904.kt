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
            val curTailNext = curTail.next.value
            if (curTail.next.compareAndSet(null, newTail)) { // failed to do CAS, means that another thread enqueued some element
                tail.compareAndSet(curTail, newTail)
                return
            } else {
                tail.compareAndSet(curTail, nodeWrapper(curTailNext))
            }
        }
    }

    private fun nodeWrapper(node: Node<E>?): Node<E> {
        if (node == null) {
            return Node(null)
        }
        return node
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: return null
            if (head.compareAndSet(curHead, curHeadNext)) {
                return curHead.x
            }
        }
    }

    fun isEmpty(): Boolean {
        // in this case CAS-2 would be the right solution but let's assume we don't know it yet
        val head = head.value
        val tail = tail.value
//        val headNext = head.next.value
        return tail == head // && headNext == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}