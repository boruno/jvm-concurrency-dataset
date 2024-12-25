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

    override fun enqueue(element: E) {
        while (true) {
            val newNode = Node(element)
            val currLast = tail.get()
            if (currLast.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currLast, newNode)
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val myHead = head.get()
            val headResolved = myHead.next
            val valueOfResolvedHead = headResolved.get()
            val newHead = valueOfResolvedHead
            val actualAssignedHead = newHead ?: Node<E>(null)
            head.compareAndSet(myHead, newHead)
            return valueOfResolvedHead?.element
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
