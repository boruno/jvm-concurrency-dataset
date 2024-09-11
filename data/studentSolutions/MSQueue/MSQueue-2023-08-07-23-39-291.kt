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
            val newNode = Node(element)
            val currTail = tail
            if (currTail.get().next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currTail.get(), newNode)
                return
            } else {
                tail.compareAndSet(currTail.get(), newNode)
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
