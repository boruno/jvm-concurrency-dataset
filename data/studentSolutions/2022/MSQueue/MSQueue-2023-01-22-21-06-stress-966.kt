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
        val newTail: Node<E> = Node(x)
        while (true) {
            val valTail = tail.value
            if (valTail!!.next.compareAndSet(null, newTail)) {
                if (newTail != null) {
                    tail.compareAndSet(valTail, newTail)
                }
                return
            }
            tail.compareAndSet(valTail, valTail.next.value!!);
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): Any? {
        while (true) {
            val curHead: Node<E> = head.value
            val curTail: Node<E> = tail.value
            val nextHead = curHead.next.value
            val nextTail = curTail.next.value
            if (nextHead == null) return Integer.MIN_VALUE
            if (head.compareAndSet(curHead as Node<E>, nextHead as Node<E>)) return nextHead.x1
            if (curHead == curTail) tail.compareAndSet(curTail, nextTail as Node<E>)
        }
    }

    fun isEmpty(): Boolean {
        val nextHead = head.value.next.value ?: return true
        return false
    }
}

private class Node<E>(val x: E?) {
    val x1 = x
    val next = atomic<Node<E>?>(null)
}

/*

package msqueue

import kotlinx.atomicfu.AtomicRef

class MSQueue : Queue {
    private val head: AtomicRef<Node>
    private val tail: AtomicRef<Node?>

    init {
        val dummy: Node = Node(0)
        head = AtomicRef(dummy)
        tail = AtomicRef(dummy)
    }

    override fun enqueue(x: Int) {
        val newTail: Node = Node(x)
        while (true) {
            val valTail = tail.value
            if (valTail!!.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(valTail, newTail)
                return
            }
            tail.compareAndSet(valTail, valTail.next.value)
        }
    }

    override fun dequeue(): Int {
        while (true) {
            val curHead = head.value
            val curTail = tail.value
            val nextHead = curHead.next.value
            val nextTail = curTail!!.next.value
            if (nextHead == null) return Int.MIN_VALUE
            if (head.compareAndSet(curHead, nextHead)) return nextHead.x
            if (curHead === curTail) tail.compareAndSet(curTail, nextTail)
        }
    }

    override fun peek(): Int {
        val nextHead = head.value.next.value ?: return Int.MIN_VALUE
        return nextHead.x
    }

    private inner class Node internal constructor(val x: Int) {
        var next = AtomicRef<Node?>(null)
    }
}*/