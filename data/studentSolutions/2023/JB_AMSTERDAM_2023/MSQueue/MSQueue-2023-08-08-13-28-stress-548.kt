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
            if (currentTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(currentTail, node)
                return
            } else {
                tail.compareAndSet(currentTail, currentTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val curTail = tail.get()
            val newHead = curHead.next.get()
            // If null - queue is empty
            if (curHead == curTail) {
                if (newHead == null)
                    return null
                else
                    tail.compareAndSet(curTail, curTail.next.get())
            } else {
                if (newHead != null && head.compareAndSet(curHead, newHead)) {
                    //curHead.next.set(null)
                    return newHead.element
                }
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
