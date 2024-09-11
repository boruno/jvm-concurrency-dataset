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
            val currentTail = tail.value
            val index = enqIdx.getAndIncrement().toInt()
            val segment = findSegment(currentTail, index / SEGMENT_SIZE)
            moveTailForward(segment)
            if (segment.cas(index % SEGMENT_SIZE, null, element)) {
                return
            }
        }
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var segment = start
        while (segment.id < id) {
            if (segment.next.value == null) {
                segment.next.compareAndSet(null, Segment(id))
            } else {
                segment = segment.next.value!!
            }
        }
        return segment
    }

    private fun moveTailForward(segment: Segment) {
        while (true) {
            val currentTail = tail.value
            if (currentTail.id < segment.id) {
                if (tail.compareAndSet(currentTail, segment)) {
                    return
                }
            }
            return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val currentHead = head.value
            val index = deqIdx.getAndIncrement().toInt()
            val segment = findSegment(currentHead, index / SEGMENT_SIZE)
            moveHeadForward(segment)
            if (segment.cas(index % SEGMENT_SIZE, null, Any())) continue
            return segment.get(index % SEGMENT_SIZE) as E
        }
    }

    private fun moveHeadForward(segment: Segment) {
        while (true) {
            val currentHead = head.value
            if (currentHead.id < segment.id) {
                if (tail.compareAndSet(currentHead, segment)) {
                    return
                }
            }
            return
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

private class Segment(val id: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

