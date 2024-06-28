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
        val node = Node(element)
        while (true) {
            val currentTail = tail.get()
            val tailNext = currentTail.next.get()
            if (currentTail.next.compareAndSet(null, node)) {
                tail.set(node)
                return
            } else {
                tail.compareAndSet(currentTail, tailNext)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.get()
            val headNext = currentHead.next.get() ?: return null
            if (!head.compareAndSet(currentHead, headNext)) continue
            return headNext.element
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
