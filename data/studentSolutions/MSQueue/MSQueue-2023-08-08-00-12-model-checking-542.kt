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
            val currTail = tail.get()
            if (currTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currTail, newNode)
                return
            } else {
                tail.compareAndSet(currTail, currTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHead = head.get()
            val currLast = tail.get()
            val nextCurrHead = currHead.next.get()

            if (currHead == currLast) {
                if (nextCurrHead == null) {
                    return null
                }
            } else {
                if (head.compareAndSet(currHead, nextCurrHead)) {
                    return nextCurrHead!!.element
                }
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
