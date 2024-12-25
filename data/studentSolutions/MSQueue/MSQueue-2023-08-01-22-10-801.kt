//package day1

import java.util.concurrent.atomic.AtomicReference

class MSQueue<E> : Queue<E> {
    private val headRef: AtomicReference<Node<E>>
    private val tailRef: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        headRef = AtomicReference(dummy)
        tailRef = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        val node = Node(element)
        while (true) {
            val tail = tailRef.get()
            val witness = tail.next.compareAndExchange(null, node)
            if (witness === null) {
                // this thread published the node
                tailRef.compareAndSet(tail, node)
                return
            } else {
//                tailRef.compareAndSet(tail, witness)
                // loop again
            }
        }
    }

    override fun dequeue(): E? {
        var head = headRef.get()
        while (true) {
            val next = head.next.get() ?: return null
            val witness = headRef.compareAndExchange(head, next)
            if (witness === head) {
                // this thread moved the pointer forward
                val result = next.element
                next.element = null // this node is now a dummy
                return result
            } else {
                head = witness
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>?>(null)
    }
}
