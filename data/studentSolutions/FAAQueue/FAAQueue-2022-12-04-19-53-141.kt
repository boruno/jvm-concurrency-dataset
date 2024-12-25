//package mpp.faaqueue

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
        while(true) {
            var curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement() // <--- FAA(enqIdx, +1)
            val segment = findSegment(curTail, curEnqIdx)

            // move tail --->
            while(true) {
                curTail = tail.value
                if(segment.id <= curTail.id) break
                if(tail.compareAndSet(curTail, segment)) break
            }
            // <--- move tail
            if(segment.cas(getSegmentIndex(curEnqIdx), null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while(true) {
            if(isEmpty) return null
            var curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement() // <--- FAA(deqIdx, +1)
            val segment = findSegment(curHead, curDeqIdx)

            // move head --->
            while(true) {
                curHead = head.value
                if(segment.id <= curHead.id) break
                if(head.compareAndSet(curHead, segment)) break
            }
            // <--- move head

            val idx = getSegmentIndex(curDeqIdx)

            if(segment.cas(idx, null, KILL()))
                continue
            return segment.get(idx) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    private fun findSegment(s: Segment, index: Long): Segment {
        val id = index / SEGMENT_SIZE

        var segment = s

        while(segment.id != id)
            synchronized(segment) {
                segment = segment.getOrCreateNext()
            }

        return segment
    }

    private fun getSegmentIndex(i: Long): Int {
        return (i % SEGMENT_SIZE).toInt()
    }
}

private class Segment(val id: Long = 0L) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)

    fun getOrCreateNext(): Segment {
        if(next != null) return next!!
        next = Segment(id+1)
        return next!!
    }
}

private class KILL

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

