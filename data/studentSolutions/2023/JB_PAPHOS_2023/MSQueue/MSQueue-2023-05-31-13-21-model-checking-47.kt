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
        val node = Node<E>(element)
        val curTail = tail
        while (true) {
            if(curTail.value.next.compareAndSet(null, node)) {
                if (tail.compareAndSet(curTail.value, node)) return
            } else {
                if (curTail.value.next.value != null) {
                    if (tail.compareAndSet(curTail.value, curTail.value.next.value!!)) return
                }
            }
        }
        TODO("implement me")
    }

    override fun dequeue(): E? {
        val curHead = head
        val curHeadNext = head.value.next
        if (curHeadNext.value == null) throw Exception()
        while (true) {
            if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                return curHeadNext.value as E
            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
