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

    override tailrec fun enqueue(element: E) {
        val curTail = tail.get()
        val node = Node(element)
        if (curTail.next.compareAndSet(null, node)) {
            tail.compareAndSet(curTail, node)
        } else {
            tail.compareAndSet(curTail, curTail.next.get())
            enqueue(element)
        }
    }

    override tailrec fun dequeue(): E? {
        val curHead = head.get()
        val nextHead = curHead.next.get() ?: return null
        return if (head.compareAndSet(curHead, nextHead)) curHead.element else dequeue()
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
