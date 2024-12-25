//package day1

import java.util.EmptyStackException
import java.util.concurrent.atomic.AtomicReference

class MSQueue<E> : Queue<E> {
    val head: AtomicReference<Node<E>>
    val tail: AtomicReference<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }


    /**
     * Insert to the queue
     */
    override fun enqueue(element: E) {
        while (true) {
            val n = Node(element)
            val curTail = tail.get()
            if (curTail.next.compareAndSet(null, n)) {
                tail.compareAndSet(tail.get(), n)
                return
            } else {
                tail.compareAndSet(tail.get(), curTail.next.get())
                return
            }
        }
    }

    /**
     * Access first and remove from queue
     *
     */
    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val curHeadNext = curHead.get().next
            if (curHeadNext.get() == null) {
                return null
            }
            if (head.compareAndSet(head.get(), curHeadNext.get())) {
                curHead.compareAndSet(curHead.get(), Node(null))
                return curHeadNext.get()?.element
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

    class Node<E>(
        var element: E?
    ) {
        val next = AtomicReference<Node<E>?>(null)
    }
}

fun main() {
    val q = MSQueue<Int>()
    q.enqueue(1)
//    q.enqueue(2)
//    q.enqueue(1)
    println(q.dequeue())
    println(q.head.get().element)
//    println(q.dequeue())
//    println(q.dequeue())
//    println(q.dequeue())
}
