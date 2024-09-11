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
            val n = tail.value
            if (tail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(n, node)
                return
            } else {
                tail.compareAndSet(n, n.next.value!!)
            }
        }
    }


    fun dequeue(): E? {
        while (true) {
            val curH = head.value
            val curT = tail.value
            val headN = curH.next.value
            if (curH == curT) {
                if (headN == null) {
                    return null
                }
                tail.compareAndSet(curT, headN)
            } else {
                if (head.compareAndSet(curH, headN!!)) {
                    return headN.x
                }
            }
        }
    }



    fun isEmpty(): Boolean {
        val head = head.value
        val tail = tail.value
        if (head == null || tail == null){
            return false
        }
        if (head.x == null || tail.x == null){
            return false
        }
        return true
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}