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
        while (true){
            val newNode = Node<E>(element)
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail, newNode)
                return
            } else {
                tail.compareAndSet(curTail, curTail.next.value as Node<E>)
            }

        }
    }

    override fun dequeue(): E? {
        while (true){
            val curHead = head.value
            val curHeadNext = curHead.next
            val nextValue = curHeadNext.value ?: return null
            if (head.compareAndSet(curHead, nextValue)) {
                val element = curHead.element
                curHead.element = null
                return element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
