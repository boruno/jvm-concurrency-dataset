package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop

class MSQueue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummyF = Node<E>(null)
        val dummyS = Node<E>(null)
        dummyF.next.value = dummyS
        dummyS.next.value = dummyF
        head = atomic(dummyF)
        tail = atomic(dummyS)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(x: E) {
        val node = Node(x)
        node.next.value = tail.value
        tail.value.next.loop { cur ->
            if (tail.value.next.value!!.next.compareAndSet(tail.value, node)) {
                tail.value.next.compareAndSet(cur, node)
                return
            } else {
                tail.value.next.compareAndSet(cur, cur!!.next.value!!)
            }
        }
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        head.value.next.loop { cur ->
            if (cur == tail.value)
                return null
            if (head.value.next.compareAndSet(cur, cur!!.next.value))
                return cur.x
        }
    }

    fun isEmpty(): Boolean {
        if (head.value.next.compareAndSet(tail.value, tail.value))
            return true
        return false
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}