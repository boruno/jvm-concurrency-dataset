//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        return if (start.id == id) {
            start
        } else {
            if (start.next.value == null)
                Segment(id+1)
            else
                start.next.value!!
        }
    }

    private fun moveTail(seg: Segment) {
        val ourTail = tail.value
        if (ourTail.id != seg.id) {
            if (ourTail.next.compareAndSet(null, seg))
                tail.compareAndSet(ourTail, ourTail.next.value!!)
        }
    }

    private fun moveHead(seg: Segment) {
        val ourHead = head.value
        if (ourHead.id != seg.id) {
            if (ourHead.next.compareAndSet(null, seg))
                tail.compareAndSet(ourHead, ourHead.next.value!!)
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val ourTail = tail.value
            val ourEnqIdx = enqIdx.getAndIncrement()
            val s = findSegment(ourTail, (ourEnqIdx / SEGMENT_SIZE).toInt())
            moveTail(s)
            if (s.cas((ourEnqIdx % SEGMENT_SIZE).toInt(),null, element)) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val ourHead = head.value
            val ourDeqIdx = deqIdx.getAndIncrement()
            val s = findSegment(ourHead, (ourDeqIdx / SEGMENT_SIZE).toInt())
            moveHead(s)
            return s.get((ourDeqIdx% SEGMENT_SIZE).toInt()) as E?: continue
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value
        }
}

private class Segment(val id: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

