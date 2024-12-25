//package day2

import day1.*
import kotlinx.atomicfu.*

private const val SEGMENT_SIZE = 16

// TODO: Copy the code from `FAABasedQueueSimplified` and implement the infinite array on a linked list of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    private val head = atomic(Node<E>()) // head of the linked list
    private val tail = atomic(Node<E>()) // tail of the linked list

    private class Node<E> {
        val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE) // create an atomic array with SEGMENT_SIZE
        val next = atomic<Node<E>?>(null) // reference to the next node
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val curIdx = enqIdx.getAndIncrement()

            val segmentIdx = curIdx % SEGMENT_SIZE

            if (segmentIdx == 0 && curIdx != 0) {
                val newTail = Node<E>()
                curTail.next.lazySet(newTail)
                tail.compareAndSet(curTail, newTail)
            }

            if (curTail.elements[segmentIdx].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val curIdx = deqIdx.getAndIncrement()

            val segmentIdx = curIdx % SEGMENT_SIZE

            if (segmentIdx == 0 && curIdx != 0) {
                head.compareAndSet(curHead, curHead.next.value!!)
            }

            val value = curHead.elements[segmentIdx].getAndSet(null) as E?
            if (value != null) return value
        }
    }
}