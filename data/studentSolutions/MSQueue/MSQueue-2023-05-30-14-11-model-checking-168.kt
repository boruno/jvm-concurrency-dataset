package day1

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
            val currTailNode = tail.value
            if (currTailNode.next.compareAndSet(null, node)) {
                tail.compareAndSet(currTailNode, node)
                return
            } else {
                tail.compareAndSet(currTailNode, currTailNode.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHeadNode = head.value
            if (currHeadNode.next.value == null) return null
            if (head.compareAndSet(currHeadNode, currHeadNode.next.value!!)) {
                return currHeadNode.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
