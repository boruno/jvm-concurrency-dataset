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
        while(true) {
            val newNode = Node(element)
            val currentTailNode = tail.value
            if (currentTailNode.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTailNode, newNode)
                return
            } else {
                tail.compareAndSet(currentTailNode, currentTailNode.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while(true) {
            val currentHead = head
            val currentNextVal = head.value.next.value ?: return null

            if (head.compareAndSet(currentHead.value, currentNextVal)) {
                return currentNextVal.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
