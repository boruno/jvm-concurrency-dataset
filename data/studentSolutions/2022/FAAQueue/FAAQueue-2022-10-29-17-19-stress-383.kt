package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(0L)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value
            val index = enqIdx.getAndAdd(1)
            val segment = findSegment(currentTail, index)

            if (tail.value.id != segment.id) {
                if (!tail.compareAndSet(tail.value, segment))
                    continue
            }

            if (segment.cas((index % SEGMENT_SIZE).toInt(), null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (isEmpty)
                return null

            val currentHead = head.value
            val index = deqIdx.getAndAdd(1)
            val segment = findSegment(currentHead, index)

            if (head.value.id != segment.id) {
                if (!head.compareAndSet(head.value, segment))
                    continue
            }

            if (segment.cas((index % SEGMENT_SIZE).toInt(), null, BROKEN))
                continue

            return segment.get((index % SEGMENT_SIZE).toInt()) as E?
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value >= enqIdx.value
        }

    private fun findSegment(startSegment: Segment, index: Long): Segment {
        val bound = startSegment.id + SEGMENT_SIZE

        if (index > bound) {
            if (startSegment.next != null) {
                return findSegment(startSegment.next!!, index)
            }

            return Segment(startSegment.id + 1)
        }

        return startSegment
    }
}

private class Segment(val id: Long) {
    var next: Segment? = null
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS
private val BROKEN = Any()