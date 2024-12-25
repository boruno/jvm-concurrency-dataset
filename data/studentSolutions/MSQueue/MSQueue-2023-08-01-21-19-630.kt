//package day1

import kotlinx.atomicfu.*
import kotlin.Exception

class MSQueue<E> : Queue<E> {
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>

    init {
        val dummy = Node<E>(null)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    //add
    override fun enqueue(element: E) {
        while (true) {
            val newNode = Node(element)
            val currentTail = tail
            val value = currentTail.value
            val next = value.next
            if (next.compareAndSet(null, newNode)) {
                tail.compareAndSet(value, newNode)
                return
            } else {
                tail.compareAndSet(value, next.value!!)
            }
        }
    }

    //remove
    override fun dequeue(): E? {
        while (true) {
            try {
            if (tail.compareAndSet(head.value, tail.value)) return null
            val curHead = head
            val curHeadNext = curHead.value.next
            val update = curHeadNext.value ?: throw Exception()
            if (head.compareAndSet(curHead.value, update)) {
                return update.element as E
            }}
            catch (e: Exception) {

            }
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
