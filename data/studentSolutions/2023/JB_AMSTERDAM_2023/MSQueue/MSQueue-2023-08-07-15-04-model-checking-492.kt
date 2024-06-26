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
        // IN
        while (true) {
            val prevTail = tail.get()
            val newNode = Node(element)
            if (tail.get().next.compareAndSet(null, newNode)) {
                if (tail.compareAndSet(tail.get(), newNode)) return
            } else {
                // we need to help here other thread
                tail.compareAndSet(prevTail, prevTail.next.get())
            }
        }
    }

    override fun dequeue(): E? {
        // OUT
        // lincheck
        //
        while (true) {
//            if (tail.get().element == null)
//            if (tail.get() == head.get()) return null
            val curHead = head.get()
            val curHeadNext = curHead.next
            if (curHeadNext == null) throw IndexOutOfBoundsException()
//            val newHead = head.get().next
//            if (newHead == null) return null
            val result = curHead.element
            if (head.compareAndSet(curHead, curHead.next.get())) {
                return curHeadNext.get()?.element
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
