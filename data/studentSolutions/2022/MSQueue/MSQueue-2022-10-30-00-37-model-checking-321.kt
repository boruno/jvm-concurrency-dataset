package mpp.msqueue

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.EmptyStackException
import java.util.NoSuchElementException

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
        val updateLast = Node(x)
        while (true) {
            val onTail = tail.value;
            val nextAflerTail = onTail.next;
            if (nextAflerTail.compareAndSet(null, updateLast)) {
                tail.compareAndSet(onTail, updateLast);
                return;
            } else {
                tail.compareAndSet(onTail, onTail.next.value!!);
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
            val curHead = head.value;
            val curTail = tail.value;
            val curHeadNext = curHead.next.value;
            if (head.value == tail.value) {
                if (curHeadNext == null) {
                    throw NoSuchElementException();
                }
                if (tail.compareAndSet(curTail, curHeadNext)) {
                    return curHeadNext.x
                }
            } else {
                if (curHeadNext == null) {
                    throw NoSuchElementException()
                } else {
                    if (head.compareAndSet(curHead, curHeadNext)) {
                        return curHeadNext.x;
                    }
                }
            }

        }
    }

    fun isEmpty(): Boolean {
        return head.value.x == tail.value.x
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}