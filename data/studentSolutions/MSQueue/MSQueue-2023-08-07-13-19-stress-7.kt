package day1

import java.util.concurrent.atomic.AtomicReference

// Michael-Scott Queue
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
            val newNode = Node(element)
            val curTail = tail.get()
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
            } else {
                tail.compareAndSet(curTail, curTail.next.get())
            }
//            val oldTail = tail.get()
//            val newTail = Node(element)
//            if (oldTail.next.compareAndSet(null, newTail)) {
//                tail.set(newTail)
//                break
//            } else {
//                tail.compareAndSet(oldTail, oldTail.next.get())
//            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.get()
            val curTail = tail.get()
            if (curHead.next.compareAndSet(curTail, curHead)) {
                if (curTail.next.get() == null) {
                    tail.compareAndSet(curTail, curTail.next.get())
                } else {
                    tail.compareAndSet(curTail, curTail.next.get())
                }
                return curHead.element
            } else {
                tail.compareAndSet(curTail, curTail.next.get())
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


//    override fun enqueue(element: E) {
//        val newNode = Node(element)
//        val oldTail = tail.get()
//        tail.compareAndSet(oldTail, newNode)
//        oldTail?.next = newNode
//    }
//
//    override fun dequeue(): E? {
//        val oldHead = head.get()
//        if (oldHead.next == null) {
//            return null
//        }
//        val newHead = oldHead.next
//        head.compareAndSet(oldHead, newHead)
//        return oldHead.value
//    }
//
//    override fun dequeue(): E? {
//        val oldHead = head.get()
//        val newHead = oldHead?.next ?: return null
//        head.compareAndSet(oldHead, newHead.get())
//        return newHead.get()?.element
//    }
