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
            val node = Node(element)
            val currentTail = tail.get()
            if (currentTail.next.compareAndSet(null, node)) {
                if (tail.compareAndSet(currentTail, node))
                    return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val newHead = curHead.next.get()
            // If null - queue is empty
            if(head.get() == tail.get() && newHead == null) {
                return null
            }
            if(newHead != null && head.compareAndSet(curHead, newHead)) {
                //curHead.next.set(null)
                return newHead.element
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
