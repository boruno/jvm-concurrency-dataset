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

    fun enqueue(x: E) {
        val newTail = Node(x)
        while (true) {
            val node: Node<E> = tail.value
            if (node.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(node, newTail)
                return
            } else {
                val nextTail: Node<E> = node.next.value ?: Node(null)
                tail.compareAndSet(node, nextTail)
            }
        }
    }

    fun dequeue(): E? {
        while (true) {
            val curHead: Node<E> = head.value
            while (true) {
                val node: Node<E> = tail.value
                val nodeNext = node.next.value
                if (nodeNext == null) {
                    break
                } else {
                    tail.compareAndSet(node, nodeNext)
                }
            }
            if (tail.value == curHead) return null
            val next: Node<E> = curHead.next.value ?: Node(null)
            if (head.compareAndSet(curHead, next)) return next.x
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    /*fun enqueue(x: E) {
        val newTail = Node(x)
        while (true) {
            val curTail = tail
            if (curTail.value.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail.value, newTail)
                return
            } else {
                val helpNewTail: Node<E> = curTail.value.next.value ?: Node(null)
                tail.compareAndSet(curTail.value, helpNewTail)
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
            val emptyNode = Node<E>(null)
            val newHead = curHeadNext ?: emptyNode
            if (head.compareAndSet(curHead, newHead)) {
                return curHead.x
            }
        }
    }*/

    fun isEmpty(): Boolean {
        return head.value.x == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}