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
        while (true) {
            val node = Node(element)
            val tail = this.tail.value
            if (tail.next.compareAndSet(null, node) &&
                this.tail.compareAndSet(tail, node)) break
            val next = tail.next.value ?: continue
            this.tail.compareAndSet(tail, next)
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val head = head.value
            val node = head.takeIf { it.element != null }
                ?: head.next.value
                ?: return null
            val newHead = node.next.value ?: continue
            if (this.head.compareAndSet(head, newHead)) {
                return node.element
            }
        }
    }

    // FOR TEST PURPOSE, DO NOT CHANGE IT.
    override fun validate() {
        check(tail.value.next.value == null) {
            "`tail.next` should be `null`"
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
