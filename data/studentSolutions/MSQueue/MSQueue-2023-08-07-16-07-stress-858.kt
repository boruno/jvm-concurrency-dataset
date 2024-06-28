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
            val curTail = tail.get()
            val next = curTail.next.get()
            if (curTail == tail.get()) {
                if (next != null) {
                    tail.compareAndSet(curTail, next)
                } else {
                    if (curTail.next.compareAndSet(null, node)) {
                        tail.compareAndSet(curTail, node)
                        return
                    }
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val curHeadNext = curHead.next.get()
            val curTail = tail.get()
            if (curHead == curTail) {
                if (curHeadNext == null) {
                    return null
                } else {
                    // helping
                    tail.compareAndSet(curTail, curHeadNext)
                }
            } else {
                if (head.compareAndSet(curHead, curHeadNext)) {
                    curHeadNext!!.next.set(null)
                    return curHeadNext.element
                }
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
