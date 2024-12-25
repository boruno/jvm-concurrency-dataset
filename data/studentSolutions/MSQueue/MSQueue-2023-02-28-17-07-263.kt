//package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

class MSQueue<E> {
    private val head = atomic<Node<E>?>(null)
    private val tail = atomic<Node<E>?>(null)

//    init {
//        val dummy = Node<E>(null)
//        head = atomic(dummy)
//        tail = atomic(dummy)
//    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        val newTail = Node(x)
        while (true) {
            val curTail = tail.value
            if (curTail == null) {
                tail.compareAndSet(curTail, newTail)
                return
            }
            val next = curTail.next.value
            if (curTail === tail.value) {
                if (next == null) {
                    if (curTail.next.compareAndSet(next, newTail) == true) {
                        tail.compareAndSet(curTail, newTail)
                        return
                    }
                } else {
                    val curValue = curTail.next.value
                    tail.compareAndSet(curTail, curValue)
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
            val curHead = head.value ?: return null
            val curTail = tail.value
            val next = curHead.next.value
            if (curHead === head.value) {
                if (curHead === curTail) {
                    if (next == null) {
                        return null
                    }
                    tail.compareAndSet(curTail, next)
                } else {
                    if (head.compareAndSet(curHead, next)) {
                        return next?.x
                    }
                }
            }
        }
    }

    fun isEmpty(): Boolean {
        while (true) {
            val curHead = head.value
            if (curHead === head.value) {
                return (curHead == null)
            }
        }
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}