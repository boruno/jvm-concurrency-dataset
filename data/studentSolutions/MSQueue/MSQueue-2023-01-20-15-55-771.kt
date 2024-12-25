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
        val node = Node(x)
        while (true) {
            if (tail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(tail.value, node)
                return
            } else {
                tail.compareAndSet(tail.value, tail.value.next.value!!)
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
            val curH = head.value
            val curT = tail.value
            val headN = curH.next.value
            if (curH == curT) {
                if (headN == null) {
                    return null
                }
                tail.compareAndSet(curT, headN)
            } else {
                if (head.compareAndSet(curH, headN!!)) {
                    return headN.x
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        val curHead = head.value
        if (curHead.x == null) {
            return true
        }
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}