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
        val tailNode = tail.get()
        if (tailNode.next.compareAndSet(null, newNode)) {
            tail.compareAndSet(tailNode, tailNode.next.get())
            return
        } else {
            tail.compareAndSet(tailNode, tailNode.next.get())
        }
        // TODO("implement me")
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val nextHead = head.get().next
            if (nextHead == null) return null
            if (head.compareAndSet(curHead.get(), nextHead.get())) {
                return nextHead.get()?.element
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
