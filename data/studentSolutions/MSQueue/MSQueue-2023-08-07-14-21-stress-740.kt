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
        while (true) {
            val currTail = tail
            val newNode = Node(element)
            if (currTail.get().next.compareAndSet(tail.get().next.get(), newNode)) {
                tail.compareAndSet(tail.get(), newNode)
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHead = head
            if (currHead.get() == null) {
                return null
            }
            if (currHead.compareAndSet(head.get(), head.get().next.get())) {
                return currHead.get().element
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
