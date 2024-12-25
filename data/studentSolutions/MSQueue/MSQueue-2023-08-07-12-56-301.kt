//package day1

import java.util.concurrent.atomic.AtomicReference

// Michael-Scott Queue
class MSQueue<E> : Queue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        val newNode = Node(element)
        val oldTail = tail.get()
        tail.compareAndSet(oldTail, newNode)
    }

    override fun dequeue(): E? {
        val oldHead = head.get()
        val newHead = oldHead?.next ?: return null
        head.compareAndSet(oldHead, newHead.get())
        return newHead.get()?.element
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
