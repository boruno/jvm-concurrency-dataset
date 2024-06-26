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
        // IN
        while (true) {
            val prevTail = tail.get()
            val newNode = Node(element)
            if (tail.get().next.compareAndSet(null, newNode)) {
                if (tail.compareAndSet(tail.get(), newNode)) return
            } else {
                // we need to help here other thread
                tail.compareAndSet(prevTail, prevTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        // OUT
        TODO("implement me")
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
