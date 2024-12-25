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
            val curTail = tail.get()
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)  // curTail, node
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.get())  // curTail, curTail.next
                return
            }
        }
    }

    override fun dequeue(): E? {
        // TODO("implement me")
        while (true) {
            val curHead = head
            val curHeadNextElem = head.get().next.get() ?: return null
            if (curHead.compareAndSet(head.get(), curHeadNextElem)) {
                return curHeadNextElem.element
            }
        }

//        while (true) {
//            val curTop = top.get() ?: return null
//            if (top.compareAndSet(curTop, curTop.next)) {
//                return curTop.element
//            }
//        }
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
