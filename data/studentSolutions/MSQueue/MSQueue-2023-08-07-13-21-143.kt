package day1

import java.lang.IllegalStateException
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
//            val newNode = Node(element)
//            val curTail = tail
//            if (curTail.get().next.compareAndSet(null, newNode)) {
//                tail.compareAndSet(curTail.get(), newNode)
//                return
//            } else {
//                tail.compareAndSet(curTail.get(), curTail.get().next.get())
//            }

            val newNode = Node(element)
            val fakeTail = tail.get()
            if (fakeTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(fakeTail, newNode)
                return
            } else {
                tail.compareAndSet(fakeTail, fakeTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val fakeHead = head
            val removedHead = fakeHead.get().next.get() ?: error("sasd")

            if (head.compareAndSet(fakeHead.get(), removedHead)) {
                return removedHead.element
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
