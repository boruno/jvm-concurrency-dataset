//package mpp.faaqueue

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
        if (s.start > tail.value.start)
            tail.compareAndSet(tail.value, s)
    }

    private fun moveHeadForward(s: Segment) {
        if (s.start > head.value.start)
            head.compareAndSet(head.value, s)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(
                start = curTail.value,
                id = (i / SEGMENT_SIZE)
            )
            moveTailForward(s)
            if (s.cas(i - s.start, null, element))
                return
        }

    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    @SuppressWarnings("unchecked")
    fun dequeue(): E? {
        while (true) {
            if (isEmpty)
                return null
            val curHead = head.value
            val i = deqIdx . getAndAdd (1)
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