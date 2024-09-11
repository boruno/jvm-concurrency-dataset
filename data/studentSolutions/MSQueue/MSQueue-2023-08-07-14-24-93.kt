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

    /*
    *   *  *  *  *
    *   H        T
    *
    *   *  *  *  *  *
    *   H        T
    *
    *   *  *  *  *  *
    *   H           T
    *
    * */

    override fun enqueue(element: E) {
        while (true) {
            val node = Node(element)
            val curTail = tail.get()
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.get())
            }
        }
    }

    /*
    *   *  *  *  *
    *   H        T
    *
    *   *  *  *  *
    *      H     T
    *
    * */

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val nextCurHead = curHead.next.get()
            if (head.compareAndSet(curHead, nextCurHead)) {
                return curHead?.element
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
