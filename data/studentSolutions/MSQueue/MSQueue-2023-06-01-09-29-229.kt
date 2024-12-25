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
        val newNode = Node<E>(element)
        val curTail = tail
        if (curTail.value.next.compareAndSet(null, newNode)){
            tail.compareAndSet(curTail.value, newNode)
            return
            }
        else{
            val nextTail = curTail.value.next.value as Node<E>;
            tail.compareAndSet(curTail.value, nextTail)
        }
    }

    override fun dequeue(): E? {
        while(true){
            val curHead = head.value
            val curHeadNext = curHead.next
            if(curHeadNext.value == null) return null
            if(head.compareAndSet(curHead, curHeadNext.value as Node<E> ))
                return curHeadNext.value!!.element
        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}
