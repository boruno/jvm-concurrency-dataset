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

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(curTail, (i / SEGMENT_SIZE).toInt())
            moveTailForward(s)
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
                return
            }
        }
    }

    private fun moveTailForward(s: Segment) {
        tail.value = s
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var curSegment = start
        for (curId in start.id + 1..id) {
            val curSegmentNext = curSegment.next
            if (curSegmentNext != null) {
                curSegment = curSegmentNext
            } else {
                val nCurSegmentNext = Segment(curId)
                curSegment.next = nCurSegmentNext
                curSegment = nCurSegmentNext
            }
        }
        return curSegment
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(curHead, (i / SEGMENT_SIZE).toInt())
            moveHeadForward(s)
            if (s.elements[(i % SEGMENT_SIZE).toInt()].compareAndSet(null, wtf)) {
                continue
            }
            return s.elements[(i % SEGMENT_SIZE).toInt()].value as E?
        }
    }

    private fun moveHeadForward(s: Segment) {
        head.value = s
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value == deqIdx.value
        }
}

private class Segment(val id: Int) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private class WTF

private val wtf = WTF()

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

