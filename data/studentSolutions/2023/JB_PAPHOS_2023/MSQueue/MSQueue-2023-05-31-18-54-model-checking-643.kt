package day1

import kotlinx.atomicfu.*
import java.util.EmptyStackException

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
            val node = Node<E>(element)
            val curTail = tail
            if (curTail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail.value, node)
                return
            }
            else {
                val next = curTail.value.next.value ?: continue
                tail.compareAndSet(curTail.value, next)
            }
        }
        TODO("implement me")
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curHeadNext = curHead.next.value ?: return null
            if (curHeadNext.let { head.compareAndSet(curHead, it) }) {
                curHeadNext.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
