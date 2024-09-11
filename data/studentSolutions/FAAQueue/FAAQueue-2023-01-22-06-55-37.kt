package mpp.faaqueue

import kotlinx.atomicfu.*

/**
 * @author : Ryannel Anna
 */
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
     * Adds the specified element [element] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndIncrement()

            val segment = findSegment(curTail, index / SEGMENT_SIZE)
            moveTailForward(segment)

            val inSegmentIndex = (index % SEGMENT_SIZE).toInt()
            if (segment.cas(inSegmentIndex, null, element)) break
        }
    }

    private fun findSegment(curTail: Segment, l: Long): Segment {
        var segment = curTail
        val indexesArray = curTail.id until l

        indexesArray.forEach{
            segment.next.compareAndSet(null, Segment(it + 1))
            segment = segment.next.value!!
        }

        return segment
    }


    private fun moveTailForward(s: Segment) {
        do {
            var curTail = tail.value
        } while (curTail.id < s.id && !tail.compareAndSet(curTail, s))
    }

    private fun moveHeadForward(s: Segment) {
        do {
            var curHead = head.value
        } while (curHead.id < s.id && !head.compareAndSet(curHead, s))
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null

//            val curHead = head.value
            val index = deqIdx.getAndIncrement()

            val segment = findSegment(head.value, index / SEGMENT_SIZE)
            moveHeadForward(segment)

            val inSegmentIndex = (index % SEGMENT_SIZE).toInt()
            if (!segment.cas(inSegmentIndex, null, NOT_VALID_SYMBOL))
                return segment.get(inSegmentIndex) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment(val id: Long) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val NOT_VALID_SYMBOL = '⊥'
const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

