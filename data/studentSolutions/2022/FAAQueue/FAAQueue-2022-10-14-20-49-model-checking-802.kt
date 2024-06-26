package mpp.faaqueue

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

    private fun findSegment(savedTailSegment: Segment, segmentId: Long): Segment {
        var curId = savedTailSegment.id
        var foundSegment = savedTailSegment
        while(curId < segmentId) {
            if(foundSegment.next.value == null) {
                val newSegment = Segment(curId)
                foundSegment.next.compareAndSet(null, newSegment)
            }
            foundSegment = foundSegment.next.value!!
            curId++
        }
        return foundSegment
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while(true) {
            var curTail = tail.value
            val ind = enqIdx.getAndIncrement()

            val segment = findSegment(curTail, ind / SEGMENT_SIZE)

            while(segment.id > curTail.id) { //move tail
                tail.compareAndSet(curTail, segment)
                curTail = tail.value
            }

            if(segment.cas((ind % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        return null
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value
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

