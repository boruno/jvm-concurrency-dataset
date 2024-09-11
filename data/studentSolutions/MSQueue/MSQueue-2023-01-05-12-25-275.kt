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
        val newNode = Node(x)

        while (true) {
            val curTail = tail.value
            val curNext = tail.value.next.value

            if (curTail == tail.value) {
                if (curNext == null) {
                    if (tail.value.next.compareAndSet(curNext, newNode)) {
                        tail.compareAndSet(curTail, newNode)
                        return
                    }
                } else {
                    tail.compareAndSet(curTail, curNext)
                }
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
            val curNext = curHead.next.value ?: return null

            if (curHead == curTail) {
                tail.compareAndSet(curTail, curNext)
            } else if (head.compareAndSet(curHead, curNext)) {
                return curHead.x
            }
        }
    }

    fun isEmpty(): Boolean {
        val curHead = head.value
        val curHeadNext = curHead.next.value
        val curTail = tail.value
        val curTailNext = curTail.next.value

        if (curTailNext != null) {
            tail.compareAndSet(curTail, curTailNext)
        }
        return curHeadNext == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}