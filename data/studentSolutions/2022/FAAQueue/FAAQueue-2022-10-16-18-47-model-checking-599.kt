package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val invalidIndex = 0;

    init {
        val firstNode = Segment(0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var currentSegment = start
        var iteration = 0
        while(true)
        {
            iteration++
            if (iteration > 1000) {
                println("Current segment" + currentSegment.id)
                throw Exception("Too much iterations in FindSegment")
            }
            if (currentSegment.id == id)
                return currentSegment
            if (currentSegment.id > id)
                throw Exception("ERROR")
            if (currentSegment.next.value == null)
            {
                currentSegment.next.compareAndSet(null, Segment(currentSegment.id + 1))
                currentSegment = currentSegment.next.value!!
            }
        }
    }

    private fun moveHeadForward(s: Segment) {
            val currentHead = head.value
            if (currentHead.id == s.id)
                return
            if (currentHead.id <= s.id)
                head.compareAndSet(currentHead, currentHead.next.value!!)
    }

    private fun moveTailForward(s: Segment) {
            val currentTail = tail.value
            if (currentTail.id == s.id)
                return
            if (currentTail.id <= s.id)
                tail.compareAndSet(currentTail, currentTail.next.value!!)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        var iteraion = 0
        while(true)
        {
            iteraion++
            if (iteraion > 1000)
                throw Exception("Too much iterations in Enqueue")
            val currentTail = tail.value
            val i = enqIdx.getAndAdd(1)
            val s = findSegment(currentTail, i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cas((i % SEGMENT_SIZE.toLong()).toInt(), null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        var iteraion = 0
        while(true) {
            if (isEmpty) return null;
            iteraion++
            if (iteraion > 1000)
                throw Exception("Too much iterations in Dequeue")
            val currentHead = head.value
            val i = deqIdx.getAndAdd(1)
            val s = findSegment(currentHead, i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cas((i % SEGMENT_SIZE.toLong()).toInt(), null, invalidIndex)) {
                continue
            }
            return s.get((i % SEGMENT_SIZE.toLong()).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val deqIdxCurrent = deqIdx.value
            val enqIdxCurrent = enqIdx.value
            if (deqIdxCurrent >= enqIdxCurrent)
                return true
            return false
        }
}

private class Segment(segmentId: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    val id = segmentId


    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

