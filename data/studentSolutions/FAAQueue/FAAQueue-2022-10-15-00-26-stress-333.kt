package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    object SkipObject

    init {
        val firstNode = Segment()
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    fun findSegment(start: Segment, id: Long): Segment {
        var newStart: Segment = start
        while (newStart.id < id) {
            if (newStart.next.value == null) {
                newStart.next.compareAndSet(null, Segment(newStart.id + 1))
            }
            newStart = newStart.next.value!!
        }
        return newStart
    }

    private fun moveTailForward(s: Segment) {
        while (s.id > tail.value.id) {
            val curTailValue = tail.value
            tail.compareAndSet(curTailValue, s)
        }
    }

    private fun moveHeadForward(s: Segment) {
        while (s.id > head.value.id) {
            val curHeadValue = head.value
            head.compareAndSet(curHeadValue, s)
        }
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s: Segment = findSegment(start = curTail, id = i / SEGMENT_SIZE)
            moveTailForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
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
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val s: Segment = findSegment(start = curHead, id = i / SEGMENT_SIZE)
            moveHeadForward(s)
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, SkipObject)) continue
            @Suppress("UNCHECKED_CAST")
            return s.get((i % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean = deqIdx.value >= enqIdx.value
}

class Segment( var id: Long = 0) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)


    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)

}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
