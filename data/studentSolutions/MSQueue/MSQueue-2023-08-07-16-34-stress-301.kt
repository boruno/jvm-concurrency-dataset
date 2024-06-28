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
            val headLocal = head.get()
            if (headLocal.next.compareAndSet(null, newNode)) {
                head.compareAndSet(headLocal, newNode)
                break;
            } else {
                val curHead = head.get()
                val next = curHead.next.get()
                if (next != null)
                    head.compareAndSet(curHead, next)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val tailElem = tail.get() ?: return null
            val first = tailElem.next.get() ?: return null
            if (first.element == null)
                return null
            if (tail.compareAndSet(tailElem, first)) {
                val elem = first.element
                first.element = null
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
