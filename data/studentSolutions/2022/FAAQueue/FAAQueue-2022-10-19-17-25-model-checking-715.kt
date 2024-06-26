package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val firstNode = Segment(start = 0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var cur = start
        while (id >= cur.start + SEGMENT_SIZE) {
            while (cur.next.value == null)
                cur.next.compareAndSet(null, Segment(cur.start + SEGMENT_SIZE))
            cur = cur.next.value!!
        }
        return cur
    }


    private fun moveTailForward(s: Segment) {
        val curTail = tail.value
        if (s.start > curTail.start)
            tail.compareAndSet(curTail, s)
    }

    private fun moveHeadForward(s: Segment) {
        val curHead = head.value
        if (s.start > curHead.start)
            head.compareAndSet(curHead, s)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(curTail, i)
            moveTailForward(s)
            if (s.cas((i - s.start).toInt(), null, element))
                return
        }
    }


    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty)
                return null
            val curHead = head.value
            val i = deqIdx.getAndAdd(1)
            val s = findSegment(curHead, i)
            moveHeadForward(s)
            if (!s.cas((i - s.start).toInt(), null, Any()))
                return s.get((i - s.start).toInt()) as? E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value
        }
}

private class Segment(val start: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS