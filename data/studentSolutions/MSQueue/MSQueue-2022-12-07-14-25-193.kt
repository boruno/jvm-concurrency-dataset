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
            val node = Node(x)
            val curTail = tail
            if (curTail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail.value, node)
                return
            } else {
                tail.compareAndSet(curTail.value, curTail.value.next.value!!)
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
            val curTail = tail
            val curHead = head
            val curHeadNext = curHead.value.next
            if (curHead.value == curTail.value && curHeadNext.value == null) {
                return null
            }
            else if (curHead.value == curTail.value) {
                curHeadNext.value?.let { tail.compareAndSet(curTail.value, it) }
            }
            else if (curHeadNext.value?.let { head.compareAndSet(curHead.value, it) } == true) {
                return curHeadNext.value?.x
            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value.next.value == null && head.value == tail.value
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}