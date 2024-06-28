package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val enqIdx = curTail.enqIdx.getAndIncrement()
            if (enqIdx < SEGMENT_SIZE)
                if (curTail.cas(enqIdx, null, element))
                    return
            val newTail = Segment()
            newTail.cas(0, null, element)
            if (curTail.next.compareAndSet(null, newTail)) {
                tail.compareAndSet(curTail, newTail)
                return
            }
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            val curHead = head.value
            val deqIdx = curHead.deqIdx.getAndIncrement()
            if (deqIdx < SEGMENT_SIZE) {
                val res = curHead.elements[deqIdx].getAndSet(null) ?: continue
                return res as E
            }
            val nextHead = curHead.next.value ?: return null
            head.compareAndSet(curHead, nextHead)
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            while (true) {
                val curHead = head.value
                if (curHead.deqIdx.value < curHead.enqIdx.value && curHead.deqIdx.value < SEGMENT_SIZE)
                    return false
                val nextHead = curHead.next.value ?: return true
                head.compareAndSet(curHead, nextHead)
            }
        }
}

private class Segment {
    val next: AtomicRef<Segment?> = atomic(null)
    val enqIdx = atomic(0)
    val deqIdx = atomic(0)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

