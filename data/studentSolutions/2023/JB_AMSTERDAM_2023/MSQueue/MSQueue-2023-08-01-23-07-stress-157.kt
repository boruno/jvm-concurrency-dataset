package day1

import kotlinx.atomicfu.*
import java.util.Objects
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
            }
//            else {
//                val valueNext = next.value
//                if (valueNext != null) {
//                    tail.compareAndSet(value, valueNext)
//                }
//            }
        }
    }

    //remove
    override fun dequeue(): E? {
        while (true) {
                val curHead = head
                val curHeadNext = curHead.value.next
                val update = curHeadNext.value ?: break
                if (head.compareAndSet(curHead.value, update)) {
                    return update.element as E
                }
        }
        return null
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
