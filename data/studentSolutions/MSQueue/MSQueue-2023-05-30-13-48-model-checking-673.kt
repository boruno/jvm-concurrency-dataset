package day1

import kotlinx.atomicfu.*
import java.lang.Exception

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
            val curTail = tail.value
            val curTailNext = curTail.next
            if (curTailNext.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                return
            }
            curTailNext.value?.let { tail.compareAndSet(curTail, it) }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val curHeadNext = curHead.value.next
            if (curHeadNext.value == null) {
                return null
            }
            if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                return curHeadNext.value?.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
