package day1

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
        while (true)
        {
            val node = Node(element)
            var curTail = tail
            if (curTail.value.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail.value, node)
                return
            } else {
                val curTailNext = curTail.value.next.value ?: continue
                tail.compareAndSet(curTail.value, curTailNext)
            }
        }

    }

    override fun dequeue(): E? {
//        while (true) {
//            val curHead = head
//            val curHeadNext = curHead.value.next
//
////            require(curHeadNext.value != null) { "Empty queue!" }
//
//            if (curHeadNext.value == null) {
//                return null
//            }
//
//            if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
//                return curHeadNext.value!!.element
//            }
//        }

//            while (true) {
//                val curHead = head.value
//                val curHeadNext = curHead.next.value ?: return null
//                if (head.compareAndSet(curHead, curHeadNext)) {
//                    return curHeadNext.element
//                }
        while (true) {
            val curHead = head
            val curHeadNext = curHead.value.next

            if (curHead.value.next.value == null)
                return null

            if (head.compareAndSet(curHead.value, curHeadNext.value!!)) {
                return curHeadNext.value!!.element
            }

        }
    }

    private class Node<E>(
        var element: E?
    ) {
        val next = atomic<Node<E>?>(null)
    }
}