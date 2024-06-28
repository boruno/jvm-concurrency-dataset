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
        val newNode = Node(element)
        val currentTail = tail.value
        if(currentTail.next.compareAndSet(null, newNode)) {
            tail.compareAndSet(currentTail, newNode)
            return
        } else {
            tail.compareAndSet(currentTail, currentTail.next.value!!)
        }
//        TODO("implement me")
        // create node
        // if tail-last -> кладём
//else - двигаем
    }

    override fun dequeue(): E? {
        while(true) {
            val currentHead = head
            val currentHeadNext = currentHead.value.next
            if (currentHeadNext.value == null) {
                throw Exception("empty queue")
            }

            if (head.compareAndSet(currentHead.value, currentHeadNext.value!!)) {
                return currentHeadNext.value!!.element
            }
        }
//        TODO("implement me")
//        проверяем, что след не null иначе ошибку
//        сдвигаем
//        while (true) {
//            val curHead = head
//            val curHeadNext = head.value.next ?: throw EmptyStackException()
//            if(head.compareAndSet(curHead.value, curHeadNext.value!!)){
//                return curHeadNext.value
//            }
//        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
