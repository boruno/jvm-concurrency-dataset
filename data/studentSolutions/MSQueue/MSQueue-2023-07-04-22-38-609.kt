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
            val node = Node(element)
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, node)) {
                if (tail.compareAndSet(curTail, node)) {
                    return
                }
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }
    }

    override fun dequeue(): E? {
         while (true) {
             val curHead = head.value
             val curHeadNext = curHead.next
             if (curHeadNext.value == null) {
                 return null
             } else {
                if (head.compareAndSet(curHead, curHeadNext.value!!)) {
                    val res = curHeadNext.value!!.element
                    curHeadNext.value = null
                    return res
                }
             }
         }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
