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
            val curTail = tail.get()
            if (curTail.next.compareAndSet(null, newNode)) {
                if (tail.compareAndSet(curTail, newNode)) return
            } else {
                tail.compareAndSet(curTail, curTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while(true) {
            val curHeadVal = head.get()
            if (head.compareAndSet(curHeadVal, curHeadVal.next.get())) {
                return curHeadVal.next.get()?.element
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
