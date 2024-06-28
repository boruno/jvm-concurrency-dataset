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
            val tailNode = tail.get()
            if (tailNode.next.compareAndSet(null, node)) {
                tail.compareAndSet(tailNode, node)
                return
            }
            tail.compareAndSet(tailNode, tailNode.next.get())
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val headNode = head.get()
            val nextNode = headNode.next.get() ?: return null
            if (head.compareAndSet(headNode, nextNode)) {
                nextNode.element.also {
                    nextNode.element = null
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
