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
        val last: Node<E> = Node(x)
//        while (true) {
//            val onTail: Node<E> = tail.value
//            if (!onTail.next.compareAndSet(null, last)) {
//                tail.compareAndSet(onTail, onTail.next.value!!)
//            } else {
//                tail.compareAndSet(onTail, last)
//                break
//            }
//        }

        do {
            val onTail: Node<E> = tail.value
            if (!onTail.next.compareAndSet(null, last)) {
                tail.compareAndSet(onTail, onTail.next.value!!)
                continue
            }
        } while (!tail.compareAndSet(onTail, last))

    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val headL: Node<E> = head.value
            val tailL: Node<E> = tail.value
            val nextH = headL.next.value ?: return null
            if (headL == tailL) {
                tail.compareAndSet(tailL, nextH)
            } else if (head.compareAndSet(headL, nextH)) {
                return nextH.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}