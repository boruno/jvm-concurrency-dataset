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
        val newNode = Node(element)
        while (true) {
            val node = tail.value
            if (node.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(node, newNode)
                return
//            } else {
//                tail.compareAndSet(node, node.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val dummy = head.value
            val value = dummy.next.value ?: return null
            if (head.compareAndSet(dummy, value)) {
                return value.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
