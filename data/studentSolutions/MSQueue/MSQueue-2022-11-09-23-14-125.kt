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
        val newNode = Node<E>(x)

        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                return
            } else {
                val next = curTail.next.value ?: return
                tail.compareAndSet(curTail, next)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while(true) {
            val curHead = head.value
            val curTail = tail.value
            val curHeadNext = head.value.next

            if (curHead == head.value) {
                if (isEmpty()) {
                    if (curHeadNext.value == null) {
                        return null
                    } else {
                        tail.compareAndSet(curTail, curHeadNext.value!!)
                    }
                } else {
                    if (curHeadNext.value == null) {
                        continue
                    }
                    val value = curHeadNext.value!!.x
                    if (head.compareAndSet(curHead, curHeadNext.value!!)) {
                        return value
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return (head.value == tail.value)
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}