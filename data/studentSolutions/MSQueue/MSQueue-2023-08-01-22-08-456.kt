//package day1

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
            } else {
                val update = next.value ?: break
                tail.compareAndSet(value, update)
            }
        }
    }

    //remove
    override fun dequeue(): E? {
        while (true) {
            try {
                //if (tail.compareAndSet(head.value, tail.value)) return null
                val curHead = head
                val curHeadNext = curHead.value.next
                val update = curHeadNext.value ?: throw Exception()
//                val update = curHeadNext.value ?: break
                if (head.compareAndSet(curHead.value, update)) {
                    return update.element as E
                }
            } catch (e: Exception) {
               return null
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
