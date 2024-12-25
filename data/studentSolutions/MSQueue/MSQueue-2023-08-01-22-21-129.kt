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
        while (true) {
            //TODO("implement me")
            var node: Node<E> = Node<E>(element)
            var curTail = tail
            if (curTail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail.value, node)
            } else {
                tail.compareAndSet(curTail.value, curTail.value.next.value!!);
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            //TODO("implement me")
            var curHead = head;
            var curHeadNext = curHead.value.next
            if (curHeadNext.value == null) return null
            else {
                if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                    return curHeadNext.value!!.element
                }
            };
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
