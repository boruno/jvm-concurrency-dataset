//package day1

import kotlinx.atomicfu.*

class MSQueue<E> : Queue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        val node = Node(element)
        while (true) {
            val last = tail.value
            val next = last.next.value
            if (last == tail.value) {
                if (next == null) {
                    if (tail.value.next.compareAndSet(null, node)) {
                        tail.compareAndSet(last, node)
                        return
                    }
                } else {
                    tail.compareAndSet(last, next)
                }
            }
        }
    }

//    override fun dequeue(): E? {
//        while (true) {
//            val start = head.value
//            val next = start.next.value
//            val last = tail.value
//            if (start == head.value) {
//                if (start == last) {
//                    if (next == null) {
//                        return null
//                    } else {
//                        tail.compareAndSet(last, next)
//                    }
//                } else {
//                    if (head.compareAndSet(start, next!!)) {
//                        return start.element
//                    }
//                }
//            }
//        }
//    }

    override fun dequeue(): E? {
        while (true) {
            val start = head.value
            val next = start.next.value
            val last = tail.value
            if (start == head.value) {
                if (start == last) {
                    if (next == null) {
                        return null
                    } else {
                        tail.compareAndSet(last, next)
                    }
                } else {
                    if (head.compareAndSet(start, next!!)) {
                        return next.element  // Corrected line
                    }
                }
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
