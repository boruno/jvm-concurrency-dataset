//package mpp.faaqueue

import kotlinx.atomicfu.*

@Suppress("UNCHECKED_CAST")
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
            val currentTailSegment = tail.value
            val enqueueIndex = enqIdx.getAndIncrement()
            val segment = findSegment(enqueueIndex, currentTailSegment)
            if (currentTailSegment != segment && !tail.compareAndSet(currentTailSegment, segment)) {
                continue
            }
            if (segment.cas((enqueueIndex % SEGMENT_SIZE).toInt(), null, element)) {
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
            val dequeueIndexedPre = deqIdx.value
            val enqueueIndexed = enqIdx.value
            if (dequeueIndexedPre >= enqueueIndexed) {
                return null
            }
            val currentHeadSegment = head.value
            val dequeueIndexed = deqIdx.getAndIncrement()
            val segment = findSegment(dequeueIndexed, currentHeadSegment)
            if (currentHeadSegment != segment && !head.compareAndSet(currentHeadSegment, segment)) {
                continue
            }
            if (segment.cas((dequeueIndexed % SEGMENT_SIZE).toInt(), null, Bad)) {
                continue
            }
            return segment.get((dequeueIndexed % SEGMENT_SIZE).toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val dequeueIndexedPre = deqIdx.value
            val enqueueIndexed = enqIdx.value
            return dequeueIndexedPre < enqueueIndexed
        }

    private fun findSegment(segmentElement: Long, currentSegment: Segment): Segment {
        var segment = currentSegment
        while (segment.firstElementId + (SEGMENT_SIZE - 1) < segmentElement) {
            val nextSegment = segment.next.value
            if (nextSegment != null) {
                segment = nextSegment
                continue
            }
            val newSegment = Segment(segment.firstElementId + SEGMENT_SIZE)
            if (segment.next.compareAndSet(null, newSegment)) {
                segment = newSegment
            }
        }
        return segment
    }
}

private class Segment(val firstElementId: Long) {
    val next = atomic<Segment?>(null)
    private val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

private object Bad

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

