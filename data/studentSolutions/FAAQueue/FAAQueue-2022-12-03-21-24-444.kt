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
        while(true) {
            val curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement() // <--- FAA(enqIdx, +1)
            val segment = findSegment(curTail, curEnqIdx)
            if(segment.getSegId() > curTail.getSegId())
                if(!tail.compareAndSet(curTail, segment)) continue
            if(segment.cas((curEnqIdx % SEGMENT_SIZE).toInt(), null, element))
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
            val curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement() // <--- FAA(deqIdx, +1)
            val segment = findSegment(curHead, curDeqIdx)
            if(segment.getSegId() > curHead.getSegId())
                tail.compareAndSet(curHead, segment)

            if(segment.cas((curDeqIdx % SEGMENT_SIZE).toInt(), null, KILL()))
                continue
            return segment.get((curDeqIdx % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value
        }

    private fun findSegment(s: Segment, index: Long): Segment {
        val id = index / SEGMENT_SIZE
        var segment = s

        while(segment.getSegId() != id || segment.getNextSeg() != null)
            segment = segment.getNextSeg()!!

        if(segment.getNextSeg() == null)
            segment = segment.createNext()

        return segment
    }
}

private class Segment(val id: Long = 0) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }

    fun getSegId(): Long {
        return id
    }

    fun createNext(): Segment {
        next = Segment(id+1)
        return next!!
    }

    fun getNextSeg(): Segment? {
        return next
    }
}

private class KILL {}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

