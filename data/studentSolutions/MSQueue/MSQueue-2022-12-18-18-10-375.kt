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
            //val node =
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, Node(x))) {
                tail.compareAndSet(curTail, Node(x))
                return
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
            val curTail = tail.value
            val curHeadNext = curHead.next.value ?: return null
            if (curHead == head.value) {
                if (curHead == curTail) {
                    tail.compareAndSet(curTail, curHeadNext);
                } else {
                    if (head.compareAndSet(curHead, curHeadNext)) {
                        return curHeadNext.x
                    }
                }
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