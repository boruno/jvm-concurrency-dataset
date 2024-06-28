package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.concurrent.atomic.AtomicReference




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
            val newTail = Node(x)
            val curTail = tail.value.next
            if (curTail.compareAndSet(null, newTail)) {
                tail.compareAndSet(tail.value, newTail)
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
//            val nextHead = head.value.next.value
//            if (head.value != tail.value) {
//                if (nextHead == null) {
//                    return null
//                } else {
//                    tail.compareAndSet(tail.value, nextHead)
//                }
//            } else {
//                val result = nextHead!!.x
//                if (head.compareAndSet(head.value, nextHead)) {
//                    return result
//                }
//            }

            val curHead = head
            val curHeadNext = head.value.next
            if (isEmpty()) {
                return null
            } else {
                if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                    return curHeadNext.value!!.x
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