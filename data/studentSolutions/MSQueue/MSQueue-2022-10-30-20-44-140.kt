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
            val curTail = tail.value
            if (curTail === tail.value) {
                val curTailNext = curTail.next.value
                if (curTailNext != null) {
                    tail.compareAndSet(curTail, curTailNext)
                } else {
                    if (curTail.next.compareAndSet(null, node)) {
                        tail.compareAndSet(curTail, node)
                        return
                    }
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
            val curHeadNext = curHead.next.value
            if (curHead === head.value) {
                val curTail = tail.value
                if (curHead != curTail) {
                    val x = curHeadNext!!.x
                    if (head.compareAndSet(curHead, curHeadNext)) {
                        return x
                    }
                } else {
                    if (curHeadNext == null) {
                        return null
                    }
                    tail.compareAndSet(curTail, curHeadNext)
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value === tail.value
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}