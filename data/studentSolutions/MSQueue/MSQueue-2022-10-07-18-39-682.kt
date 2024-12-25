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
        val node = Node(x);
        var curTail: Node<E>
        var curNext: Node<E>?
        while (true) {
            curTail = tail.value;
            curNext = curTail.next.value;
            if (curTail == tail.value) {
                if (curNext == null) {
                    if (curTail.next.compareAndSet(null, node)) {
                        break;
                    }
                } else {
                    tail.compareAndSet(curTail, node);
                }
            }
        }
        tail.compareAndSet(curTail, node);
    }

    /**
     * Retrieves the first element from the queue
     * and returns it; returns `null` if the queue
     * is empty.
     */
    fun dequeue(): E? {
        var curHead: Node<E>
        var res: E?
        while(true)
        {
            curHead = head.value;
            var curTail = tail.value;
            var curNext = curHead.next.value;
            if (head.value == curHead)
            {
                if (curHead == curTail)
                {
                    if (curNext == null)
                    {
                        return null
                    }
                    tail.compareAndSet(curTail, curNext);
                }
                else
                {
                    res = curNext?.x;
                    if (head.compareAndSet(curHead, curNext as Node<E>)) {
                        break;
                    }
                }
            }
        }
        return res
    }

    fun isEmpty(): Boolean {
        return (tail.value == head.value);
    }
}

private class Node<E>(val x: E?) {
    val next = atomic<Node<E>?>(null)
}