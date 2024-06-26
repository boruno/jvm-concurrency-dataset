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
        TODO("implement me")
        while(true) {
            val node = MSQueue.Node(element)
            val curtail = tail.value
            if (curtail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curtail,node)
                return
            } else {
                curtail.next.compareAndSet(curtail, curtail.next.value)
            }
        }
    }

    override fun dequeue(): E? {
        TODO("implement me")
        while(true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value
            if (curHeadNext == null) return null
            if (head.compareAndSet(curHead,curHeadNext)) {
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
