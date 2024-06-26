package day1

import java.util.concurrent.atomic.AtomicReference

class MSQueue<E> : Queue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {

    }

    // dequeue doesn't care about the tail.
    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.get()
            // we treat currentHead as a dummy
            val currentHeadPointsTo = currentHead.next.get()
            if (currentHeadPointsTo == null) {
                // the list is empty.
                return null
            }
            val retVal = currentHeadPointsTo.element
            // we attempt to move the head pointer
            if (head.compareAndSet(currentHead, currentHeadPointsTo)) {
                return retVal
            }
        }
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
