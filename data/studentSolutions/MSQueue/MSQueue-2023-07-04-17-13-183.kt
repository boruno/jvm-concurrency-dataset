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
//        TODO("implement me")
        val elementNode = Node(element)

        while (true) {
            val curTail = tail.value
            val next = curTail.next

            if (next.compareAndSet(null, elementNode)) {
                tail.compareAndSet(curTail, elementNode)
            } else {
                tail.compareAndSet(curTail, next.value!!)
            }
        }

    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val next = curHead.next.value
            if (next?.element == null) {
                return null //no element
            }
            if (head.compareAndSet(curHead, next)) {
                curHead.element = null
            }
        }

//        TODO("implement me")
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
