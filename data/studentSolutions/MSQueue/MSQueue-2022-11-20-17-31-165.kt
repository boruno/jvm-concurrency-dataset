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
        val newNode = Node(x)
        while (true) {
            val curTail = tail
            val next = curTail.value.next
            if (curTail.value == tail.value) {
                if (next.value == null) {
                    if(curTail.value.next.compareAndSet(next.value, newNode)) {
                        break
                    }
                } else {
                    tail.compareAndSet(curTail.value, next.value!!)
                }
            }
        }
        tail.compareAndSet(tail.value, Node(null))
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head
            val curTail = tail
            val next = head.value.next;
            if (curHead.value == head.value) {
                if(head.value == tail.value) {
                    if (next.value == null) {
                        return null
                    }
                    // CAS(&Q–>Tail, tail, <next.ptr, tail.count+1>)
                    tail.compareAndSet(curTail.value, next.value!!)
                } else {
                    val res = next.value?.x
                    // CAS(&Q–>Head, head, <next.ptr, head.count+1>)
                    if (head.compareAndSet(curHead.value, next.value!!)) {
                        return res
                    }
                }

            }
        }
    }

    fun isEmpty(): Boolean {
        return head.value == tail.value;
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}