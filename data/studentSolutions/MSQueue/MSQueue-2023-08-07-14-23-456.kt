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
        // TODO("implement me")
        while (true) {
            val node = Node(element)
            val curTail = tail
            if (curTail.get().next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail.get(), node)  // curTail, node
                return
            } else {
                tail.compareAndSet(curTail.get(), curTail.get().next.get())  // curTail, curTail.next
                return
            }
        }
    }

    override fun dequeue(): E? {
        // TODO("implement me")
        while (true) {
            val curHead = head
            val curHeadNext = head.get().next
            val curHeadNextElem = curHeadNext.get()
            if (curHeadNext.get() == null) {return null}
            if (curHead.compareAndSet(head.get(), curHeadNext.get())) {
                return curHeadNextElem?.element
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
