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

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndAdd(1)
            val segment = findSegment(curTail, index / SEGMENT_SIZE)
            moveTailForward(segment)
            if (segment.elements[(index % SEGMENT_SIZE).toInt()].compareAndSet(null, element)) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val curHead = head.value
            val index = deqIdx.getAndAdd(1)
            val segment = findSegment(curHead, index / SEGMENT_SIZE)
            moveHeadForward(segment)
            val result = segment.elements[(index % SEGMENT_SIZE).toInt()].getAndSet(0) ?: continue
            return result as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return enqIdx.value <= deqIdx.value
        }


    private fun moveHeadForward(segment: Segment) {
        while (true) {
            val curHead = head.value
            if (curHead.id >= segment.id) return
            head.compareAndSet(curHead, segment)
        }
    }

    private fun moveTailForward(segment: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.id >= segment.id) return
            tail.compareAndSet(curTail, segment)
        }
    }

    private fun findSegment(start: Segment, index: Long): Segment {
        var curIndex = start.id
        var curSegment: Segment = start
        while (curIndex < index) {
            if (curSegment.next.value == null) {
                val newSegment = Segment(curIndex + 1)
                curSegment.next.compareAndSet(null, newSegment)
            } else {
                curSegment = curSegment.next.value!!
                curIndex = curSegment.id
            }
        }
        return curSegment
    }
}

private class Segment(val id: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    private fun get(i: Int) = elements[i].value
    private fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

