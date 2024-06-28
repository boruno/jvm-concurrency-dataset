package day1

import java.util.concurrent.atomic.AtomicReference

class MSQueue<E> : Queue<E> {
    private val headRef: AtomicReference<Node<E>>
    private val tailRef: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(Any() as E)
        headRef = AtomicReference(dummy)
        tailRef = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        val node = Node(element)
        var tail = tailRef.get()
        while (true) {
            val witness = tail.next.compareAndExchange(null, node)
            if (witness === null) {
                // this thread published node
                tailRef.compareAndSet(tail, node)
                return
            } else {
                // another thread published node, we've witnessed it
                tailRef.compareAndSet(tail, witness)
                tail = witness // loop again
            }
        }
    }

    override fun dequeue(): E? {
        var head = headRef.get()
        while (true) {
            val next = head.next.get() ?: return null
            val witness = headRef.compareAndExchange(head, next)
            if (witness === head) {
                return witness.element
            } else {
                head = witness
            }
        }
    }

    private class Node<E>(
        val element: E
    ) {
        val next = AtomicReference<Node<E>?>(null)
    }
}
