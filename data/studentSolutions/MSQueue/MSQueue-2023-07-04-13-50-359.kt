//package day1

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
        //TODO("implement me")
        val curTail = tail.value
        val curTailNext = curTail.next.value
        val newNode = Node(element)
        if (curTail.next.compareAndSet(null, newNode)) {
            tail.compareAndSet(curTail, newNode)
        } else {
            tail.compareAndSet(curTail, curTailNext!!)
        }
    }

    override fun dequeue(): E? {
        //TODO("implement me")
        val curHead = head.value
        val curHeadNext = curHead.next.value
        return if (curHeadNext == null) throw EmptyStackException()
        else if (head.compareAndSet(curHead, curHeadNext)) curHeadNext.element
        else dequeue()
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
