//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    private val lock = ReentrantLock()

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        lock.lock()
        try {
            val newNode = Node(x)

            while (true) {
                val curTail = tail.value
                val curTailNext = curTail.next.value

                if (curTail == tail.value) {
                    if (curTailNext == null) {
                        if (curTail.next.compareAndSet(null, newNode)) {
                            tail.compareAndSet(curTail, newNode)
                            return
                        }
                    } else {
                        tail.compareAndSet(curTail, curTailNext)
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        lock.lock()
        try {
            while (true) {
                val curHead = head.value
                val curTail = tail.value
                val curHeadNext = curHead.next.value

                if (curHead == head.value) {
                    if (curHead == curTail) {
                        if (curHeadNext == null) {
                            return null
                        }
                        tail.compareAndSet(curTail, curHeadNext)
                    } else if (head.compareAndSet(curHead, curHeadNext!!)) {
                        return curHead.x
                    }
                }
            }
        } finally {
            lock.unlock()
        }
    }

    fun isEmpty(): Boolean {
        val curHead = head.value
        val curHeadNext = curHead.next.value
        val curTail = tail.value

        return curHead == curTail && curHeadNext == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}