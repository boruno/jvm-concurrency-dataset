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
        while (true) {
            val newNode = Node(element)
            val curTail = tail
            if (curTail.value.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(curTail.value, newNode)
                break
            } else {
                tail.compareAndSet(curTail.value, curTail.value.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val curHeadNext = curHead.value.next.let { it.value ?: return null }
            if (head.compareAndSet(curHead.value, curHeadNext)) {
                return curHeadNext.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
