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
            val curTailRef = tail
            val curTail = curTailRef.get()
            val nextRef = curTail.next
            if (nextRef.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                return
            } else {
                tail.compareAndSet(tail.get(), tail.get().next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHeadRef = head
            val curHead = curHeadRef.get()
            val nextRef = curHead.next
            val next = nextRef.get() ?: return null
            if (head.compareAndSet(curHead, next)) {
                return curHead.element
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
