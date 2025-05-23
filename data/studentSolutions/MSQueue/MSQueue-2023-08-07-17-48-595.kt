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
        val node = Node(element)
        while (true) {
            val curTail = tail.get()
            val next = curTail.next
            if (next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            }
            next.compareAndSet(next.get(), node)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val next = curHead.next.get() ?: return null
            if (head.compareAndSet(curHead, next)) {
                return next.element.also { next.element = null }
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
