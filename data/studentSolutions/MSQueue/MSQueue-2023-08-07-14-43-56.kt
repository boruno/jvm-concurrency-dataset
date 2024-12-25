//package day1

import java.util.EmptyStackException
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
            val currentTail = tail.get()
            val newTail = Node(element)
            if (currentTail.next.get() == null) {
                currentTail.next.set(newTail)
                if (tail.compareAndSet(currentTail, newTail))
                    return
            } else {
                if (tail.compareAndSet(currentTail, currentTail.next.get()))
                    return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get() ?: return null
            // If null - queue is empty
            val newHead = curHead.next.get()
            if(head.compareAndSet(curHead, newHead))
                return curHead.element
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
