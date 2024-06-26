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
        val curTail = tail.get()
        while(!curTail.next.compareAndSet(null, Node<E>(element)))
        {
            tail.compareAndSet(curTail, curTail.next.get())
        }


    }

    override fun dequeue(): E? {
        var curTop = head.get() ?: return null
        while (!head.compareAndSet(curTop, curTop.next.get()))
            curTop = head.get()?: return null

        return curTop.element
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
