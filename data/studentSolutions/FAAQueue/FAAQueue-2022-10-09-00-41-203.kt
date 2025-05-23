//package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E : Any> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(curTail, i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cas(i % SEGMENT_SIZE, null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) {
                return null
            }
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(curHead, i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cas(i % SEGMENT_SIZE, null, Object())) {
                continue
            }
            return s.get(i % SEGMENT_SIZE) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value
        }

    private fun findSegment(segment: Segment, id: Long): Segment {
        var s = segment
        while (true) {
            if (s.id == id) {
                return s
            }
            val new = Segment(s.id + 1)
            s.next.compareAndSet(null, new)
            s = s.next.value!!
        }
    }

    private fun moveTailForward(segment: Segment) {
        while (true) {
            val curTail = tail.value
            val next = curTail.next.value
            if ((curTail.id >= segment.id) || next?.let { tail.compareAndSet(curTail, it) } == true) {
                break
            }
        }
    }

    private fun moveHeadForward(segment: Segment) {
        while (true) {
            val curHead = head.value
            val next = curHead.next.value
            if ((curHead.id >= segment.id) || next?.let { head.compareAndSet(curHead, it) } == true) {
                break
            }
        }
    }
}

private class Segment(var id: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)

    fun get(i: Long) = elements[i.toInt()].value
    fun cas(i: Long, expect: Any?, update: Any?) = elements[i.toInt()].compareAndSet(expect, update)
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

