//package day1

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
            val curHeadNode = head.get()
            if (curHeadNode.next.compareAndSet(null, newNode)) {
                head.compareAndSet(curHeadNode, newNode)
                return
            } else {
                head.compareAndSet(curHeadNode, curHeadNode.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curTail = tail.get()
            if (curTail == null) throw NullPointerException()
            val tailNext = curTail.next.get()
            if (tailNext == null) throw NullPointerException()
            if (tail.compareAndSet(curTail, tailNext)) {
                val r = tailNext.element
                tailNext.element = null  // memory-leak avoiding
                return r
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
