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
        val node = Node(element)
        while (true) {
            val curTail = tail
            if (curTail.get().next.compareAndSet(null, node)) {
                tail.set(node)
                return
            }
        }
    }

    override fun dequeue(): E? {
        while (true){
            val curHead = head
            val curHeadNext = curHead.get().next
            if (curHeadNext == null)
                return null
            if (head.compareAndSet(curHead.get(), curHeadNext.get()))
                return curHeadNext.get()?.element
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
