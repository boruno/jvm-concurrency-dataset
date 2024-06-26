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
            val segmentIdx = idx / SEGMENT_SIZE

            while (curTail.next.value != null && curTail.idx < segmentIdx) {
                curTail = curTail.next.value!!
            }
            if (curTail.idx < segmentIdx) {
                val newSegment = Segment(segmentIdx)
                if (curTail.next.compareAndSet(null, newSegment)) {
                    tail.compareAndSet(curTail, newSegment)
                }
                curTail = curTail.next.value!!
            }

            if (curTail.elements[(idx % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) {
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
            if (enqIdx.value <= deqIdx.value) {
                return null
            }

            var curHead = head.value
            val idx = deqIdx.getAndIncrement()
            val segmentIdx = idx / SEGMENT_SIZE

            while (curHead.next.value != null && curHead.idx < segmentIdx) {
                curHead = curHead.next.value!!
            }
            if (curHead.idx < segmentIdx) {
                val newSegment = Segment(segmentIdx)
                if (curHead.next.compareAndSet(null, newSegment)) {
                    head.compareAndSet(curHead, newSegment)
                }
                curHead = curHead.next.value!!
            }

            if (curHead.elements[(idx % SEGMENT_SIZE).toInt()].compareAndSet(null, BROKEN_MARKER)) {
                continue
            } else {
                return curHead.elements[(idx % SEGMENT_SIZE).toInt()].value as E?
            }
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() = (deqIdx.value <= enqIdx.value)
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
private const val BROKEN_MARKER = ""

