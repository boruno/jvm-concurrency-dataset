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
        val node = Node(element)
        val currTail = tail
        if (currTail.value.next.compareAndSet(null, node)){
            tail.compareAndSet(currTail.value, node)
            return
        } else {
            currTail.value.next.value?.let { tail.compareAndSet(currTail.value, it) }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            val currHead = head
            val currHeadNext = currHead.value.next
            if (currHead.value == null){
                throw EmptyStackException()
            }
            if (currHeadNext.value?.let { head.compareAndSet(currHead.value, it) } == true){
                return currHeadNext.value as E
            }
        }
        TODO("implement me")
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
