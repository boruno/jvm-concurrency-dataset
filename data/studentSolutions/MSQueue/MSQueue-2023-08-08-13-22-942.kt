//package day1

import java.util.EmptyStackException
import java.util.concurrent.atomic.AtomicReference

class MSQueue<E> : Queue<E> {
    private val head: AtomicReference<Node<E>>
    private val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    /**
     * Insert to the queue
     */
    override fun enqueue(element: E) {
        val n = Node(element)
        val curTail = tail.get()
        if (curTail.next.compareAndSet(null, n)) {
            tail.compareAndSet(curTail, n)
            return
        } else {
            tail.compareAndSet(curTail, curTail.next.get())
            return
        }
    }

    /**
     * Access first and remove from queue
     *
     *
     */
    override fun dequeue(): E? {
        while(true) {
            val curHead = head.get()
            val curHeadNext = curHead.next.get()
            if (curHeadNext == null) {
                return null
            }
            if (head.compareAndSet(curHead, curHeadNext)) {
                return curHeadNext.element
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

fun main() {
    val q = MSQueue<Int>()
    q.enqueue(1)
    q.enqueue(2)
    q.enqueue(1)
    println(q.dequeue())
    println(q.dequeue())
    println(q.dequeue())
    println(q.dequeue())
}
