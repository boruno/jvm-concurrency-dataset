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
        val newElement = Node(x)

        while (true) {
            val currTail = tail.value
            if (tail.value.next.compareAndSet(null, newElement)) {
                tail.compareAndSet(currTail, newElement)
                return
            } else {
                currTail.next.value?.let { tail.compareAndSet(currTail, it) }
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
            val currHead = head.value

            if (currHead.next.value != null) {
                if (head.compareAndSet(currHead, currHead.next.value!!)) {
                    return currHead.x
                }
//                if (currHead.next.value?.let { head.compareAndSet(currHead, it) } == true) {
//                    return currHead.x
//                }
            } else {
                return null
            }
        }
    }

    fun isEmpty(): Boolean {
        val currHead = head.value
        return currHead.next.value == null
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}

fun main() {
    val a = MSQueue<Int>()
    a.enqueue(1)
    assert(!(a.isEmpty()))
    a.enqueue(2)
    a.enqueue(3)
    a.enqueue(2)
    assert(a.dequeue() == 1)
    assert(a.dequeue() == 2)
    assert(a.dequeue() == 3)
    assert(a.dequeue() == 2)
}