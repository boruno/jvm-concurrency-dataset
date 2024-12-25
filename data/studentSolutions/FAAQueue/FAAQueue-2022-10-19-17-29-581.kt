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
            val curTail = tail
            val i = enqIdx.getAndIncrement()
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
            if (deqIdx.value <= enqIdx.value) return null
            val curHead = head.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(
                start = curHead,
                id = i / SEGMENT_SIZE
            )
            moveHeadForward(s)
            if (!s.cas(i - s.start, null, Any())) {
                val get = s.get(i - s.start)
                return get as? E
            }
        }

    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
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