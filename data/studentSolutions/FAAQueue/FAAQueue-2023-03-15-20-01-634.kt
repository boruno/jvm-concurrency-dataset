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

    /** Adds the specified element [x] to the queue.
    */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val index = enqIdx.getAndIncrement()

            val segment = findSegment(curTail, index / SEGMENT_SIZE)
            if (segment.id > curTail.id) {
                tail.compareAndSet(curTail, segment)
            }

            val elemInd = (index % SEGMENT_SIZE).toInt()
            if (segment.cas(elemInd, null, element)) return
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var segment = start
        for (i in segment.id until id) {
            if (segment.next.value == null) {
                segment.next.compareAndSet(null, Segment(i + 1))
            } else {
                segment = segment.next.value!!
            }
        }
        return segment
    }

    /** Retrieves the first element from the queue and returns it;
    * returns null if the queue is empty.
    */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty) return null
            val curHead = head.value
            val index = deqIdx.getAndIncrement()

            val segment = findSegment(curHead, index / SEGMENT_SIZE)
            if (segment.id > curHead.id) {
                head.compareAndSet(curHead, segment)
            }

            val elemInd = (index % SEGMENT_SIZE).toInt()
            if (segment.cas(elemInd, null, Any())) {
                continue
            }

            return segment.get(elemInd) as E?
        }
    }

    /**
     * Returns true if this queue is empty, or false otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }
}

private class Segment(val id: Long = 0) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)
    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS