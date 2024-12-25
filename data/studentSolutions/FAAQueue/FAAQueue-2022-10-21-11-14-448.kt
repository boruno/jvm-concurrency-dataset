//package mpp.faaqueue

import kotlinx.atomicfu.*

// 0 1 2 3 | 4 5 6 7 |

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val bottom = -1999937

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
            val globalPos = enqIdx.getAndAdd(1)
            val segment = findSegment(curTail, globalPos / SEGMENT_SIZE)
            moveTailForward(segment)
            if (segment.cas((globalPos % SEGMENT_SIZE).toInt(), null, element))
                return
        }
    }

    private fun findSegment(start: Segment, needSegmentId: Long): Segment {
        var seg = start
        while (seg.id < needSegmentId) {
            if (seg.next.value == null) {
                val nxt = Segment(seg.id + 1)
                seg.next.compareAndSet(null, nxt)
            }
            seg = seg.next.value!!
        }
        return seg
    }

    private fun moveTailForward(tailCandidate: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.id >= tailCandidate.id) return
            if (tail.compareAndSet(curTail, tailCandidate))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val curHead = head.value
            val globalPos = deqIdx.getAndAdd(1)
            val segment = findSegment(curHead, globalPos / SEGMENT_SIZE)
            moveHeadForward(segment)
            if (segment.cas((globalPos % SEGMENT_SIZE).toInt(), null, bottom))
                continue
            return segment.get((globalPos % SEGMENT_SIZE).toInt()) as E?
        }
    }

    private fun moveHeadForward(headCandidate: Segment) {
        while (true) {
            val curHead = head.value
            if (curHead.id >= headCandidate.id) return
            if (head.compareAndSet(curHead, headCandidate))
                return
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

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

