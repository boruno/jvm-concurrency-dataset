package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

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
            var curTail = tail.value
            val idx = enqIdx.getAndIncrement()

            val segmentIdx = idx.div(SEGMENT_SIZE)
            curTail = findSegment(curTail, segmentIdx)
            curTail.next.value?.let { tail.compareAndSet(curTail, it) }

            val elementIdx = idx.mod(SEGMENT_SIZE)
            if (curTail.elements[elementIdx].compareAndSet(null, element)) {
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
            if (isEmpty) return null

            var curHead = head.value
            val idx = deqIdx.getAndIncrement()

            val segmentIdx = idx.div(SEGMENT_SIZE)
            curHead = findSegment(curHead, segmentIdx)

            val elementIdx = idx.mod(SEGMENT_SIZE)
            if (elementIdx == SEGMENT_SIZE - 1 && curHead.next.value != null) {
                head.compareAndSet(curHead, curHead.next.value!!)
            }

            val res = curHead.elements[elementIdx].getAndSet(DONE)
            res?.let { return it as E? } ?: continue
        }
    }

    private fun findSegment(segment: Segment, idx: Long): Segment {
        var tmpSegment = segment
        do {
            tmpSegment.next.compareAndSet(null, Segment(tmpSegment.idx + 1))
            tmpSegment = tmpSegment.next.value!!
        } while (tmpSegment.idx < idx)

        return tmpSegment
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = deqIdx.value >= enqIdx.value
}

private class Segment(val idx: Long = 0L) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
private const val DONE = 0
