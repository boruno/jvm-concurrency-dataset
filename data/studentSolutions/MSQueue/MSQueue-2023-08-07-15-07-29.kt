//package day1

import java.util.EmptyStackException
import java.util.concurrent.atomic.AtomicReference
// lincheck - test tool
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
            val currentTailHead = tail.get()
            val nextNode = currentTailHead.next.get()
            if (tail.compareAndSet(nextNode, newNode)) {
                tail.compareAndSet(currentTailHead, newNode)
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.get()
            val nextHead = head.get().next.get()
            if (currentHead == null) throw EmptyStackException()
            if (head.compareAndSet(currentHead, nextHead)) {
                return currentHead.element
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
