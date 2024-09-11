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
            val node = Node(element)
            val curTail = tail
            val next = curTail.value.next
            if (next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail.value, node)
                return
            } else {
                tail.compareAndSet(curTail.value, next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val curHead = head
            val next = curHead.value.next
            if ( next.value == null ) return null
            if (head.compareAndSet(curHead.value, next.value!!)) {
                return curHead.value.element
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
