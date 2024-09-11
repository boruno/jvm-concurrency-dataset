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
            val currentTailRef = tail.get()
            if (currentTailRef.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTailRef, newNode)
                return
            }
            else {
                tail.compareAndSet(currentTailRef, currentTailRef.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val headRef = head.get()
            val headRefNext = headRef.next.get() ?: return null
            val value = headRefNext.element ?: return null
            if (head.compareAndSet(headRef, headRef.next.get())) {
                head.compareAndSet(headRefNext, null)
                return value
            }
        }
    }

    // FOR TEST PURPOSE, DO NOT CHANGE IT.
    override fun validate() {
        check(tail.get().next.get() == null) {
            "At the end of the execution, `tail.next` must be `null`"
        }
        check(head.get().element == null) {
            "At the end of the execution, the dummy node shouldn't store an element"
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>?>(null)
    }
}
