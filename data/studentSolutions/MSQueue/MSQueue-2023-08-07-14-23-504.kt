//package day1

import java.util.concurrent.atomic.AtomicReference

class MSQueue<E> : Queue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    /**
     * Get last without removal
     */
    override fun enqueue(element: E) {
        val n = Node(element)
        val curTail = tail.get()
        val curTailNext = curTail.next.get()
        if (curTailNext == null) {
            curTail.next.compareAndSet(null, n)
        } else {
            curTailNext.next.compareAndSet(null, n)
        }
    }

    /**
     * Remove from queue
     *
     * head ->
     *
     */
    override fun dequeue(): E? {
        while(true) {
            val curHead = head.get()
            val curHeadNext = curHead.next.get()
            if (head.compareAndSet(curHead, curHeadNext)) {
                return curHead.element
            }
        }
        return null
    }

    // FOR TEST PURPOSE, DO NOT CHANGE IT.
    override fun validate() {
        check(tail.get().next.get() == null) {
            "`tail.next` must be `null`"
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>?>(null)
    }
}
