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
            val currTail = tail
            val next = currTail.get().next
            if (next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currTail.get(), newNode)
                return
            } else {
                tail.compareAndSet(currTail.get(), next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHead = head
            val nextCurrHead = currHead.get().next
            if (nextCurrHead.get() == null) {
                return null
            }
            if (head.compareAndSet(currHead.get(), nextCurrHead.get())) {
                return currHead.get()!!.element
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
