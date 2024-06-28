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
        val newNode = Node(element)
        while (true) {
            val tailLocal = tail.get()
            if (tailLocal.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(tailLocal, newNode)
                break;
            } else {
                val next = tailLocal.next.get()
                tail.compareAndSet(tailLocal, next)
            }
        }
    }

    override fun dequeue(): E? {
        if (head == tail)
            return null
        while (true) {
            val headElem = head.get()
            if (head.compareAndSet(headElem, headElem.next.get())) {
                val elem = headElem.element
                headElem.element = null
                return elem
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
