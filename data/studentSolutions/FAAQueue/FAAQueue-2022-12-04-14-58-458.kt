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
            val curTail = tail.value
            val curEnqIdx = enqIdx.getAndIncrement() // <--- FAA(enqIdx, +1)
            val segment = findSegment(curTail, curEnqIdx)

            if(segment.id > curTail.id)
                tail.compareAndSet(curTail, segment)

            val pos = (curEnqIdx % SEGMENT_SIZE).toInt()

            val el = segment.elements[pos]

            if(el.compareAndSet(null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while(true) {
            if(deqIdx.value >= enqIdx.value) return null
            val curHead = head.value
            val curDeqIdx = deqIdx.getAndIncrement() // <--- FAA(deqIdx, +1)
            val segment = findSegment(curHead, curDeqIdx)
            if(segment.id > curHead.id)
                head.compareAndSet(curHead, segment)

            val pos = (curDeqIdx % SEGMENT_SIZE).toInt()

            val el = segment.elements[pos]

            if(el.compareAndSet(null, KILL()))
                continue
            return el.value as E
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

        if(s.id == id)
            return s

        var nextPair = s.getOrCreateNext()

        while(nextPair.first.id != id)
            nextPair = nextPair.first.getOrCreateNext()

        return nextPair.first
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

    /**
     * Returns the next segment and a boolean value
     * which represents if the new segment was created
     */
    fun getOrCreateNext(): Pair<Segment, Boolean> {
        if(next != null) return Pair(next!!, false)
        next = Segment(id+1)
        return Pair(next!!, true)
    }
}

private class KILL

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

