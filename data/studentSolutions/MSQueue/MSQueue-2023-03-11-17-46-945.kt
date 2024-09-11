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
        val node = Node(x)
        while (true) {
            val n = tail.value
            if (tail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(n, node)
                return
            } else {
                tail.compareAndSet(n, n.next.value!!)
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
            val nodeHead = head.value
            val nodeNextHead = nodeHead.next.value
            if (nodeHead == tail.value) {
                val nodeTail = tail.value
                if (nodeNextHead != null) {
                    tail.compareAndSet(nodeTail, nodeNextHead)
                } else {
                    return null
                }
            }
            else {
                if (head.compareAndSet(nodeHead, nodeNextHead!!)) {
                    return nodeNextHead.x
                }
            }
        }
    }



    fun isEmpty(): Boolean {
        val curHead = head.value
        curHead.next.value ?: return true
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}