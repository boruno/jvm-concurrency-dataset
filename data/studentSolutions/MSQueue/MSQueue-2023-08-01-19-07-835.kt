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
        while (true) {
            val newNode = Node(element)
            val currentTail = tail.value
            if (tail.value.next.compareAndSet(null, newNode)) {
                tail.compareAndSet(currentTail, newNode)
                return
            } else {
                currentTail.next.value?.let {
                    tail.compareAndSet(currentTail, it)
                }
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currentHead = head.value
            val currentHeadNext = currentHead.next.value ?: throw EmptyQueueException()

            if (head.compareAndSet(currentHead, currentHeadNext))
                return currentHeadNext.element
        }
    }

    /**
     * while (true) {
     *     curHead := head
     *     curHeadNext := curHead.next
     *     if curHeadNext == null:
     *       throw EmptyQueueException()
     *     if CAS(&head, curHead, curHeadNext):
     *       return curHeadNext.value
     *   }
     * }
     * dummyN
     * 1 N
     * tail
     * 2 N
     */
}

private class Node<E>(
    var element: E?
) {
    val next = atomic<Node<E>?>(null)
}


class EmptyQueueException : Throwable() {

}
