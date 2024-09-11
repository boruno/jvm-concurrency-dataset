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
            val _tail = tail.value
            val index = enqIdx.getAndIncrement()
            val segment = findSegment(_tail, index / SEGMENT_SIZE)
            if (tail.value.id < index && !tail.compareAndSet(_tail, segment)) continue
            if (segment.cas((index % SEGMENT_SIZE),null, element)) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val _head = head.value
            val index = deqIdx.getAndIncrement()
            val segment = findSegment(_head, index / SEGMENT_SIZE)
            if (head.value.id < index && !head.compareAndSet(_head, segment)) continue
            if (segment.cas(index % SEGMENT_SIZE,null, Dummy())) continue
            @Suppress("UNCHECKED_CAST")
            return segment.get(index % SEGMENT_SIZE) as E
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var segment = start
        while (segment.id < id) {
            if (segment.next.value == null) {
                segment.next.compareAndSet(null, Segment(segment.id + 1))
            }
            segment = segment.next.value!!
        }
        return segment
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
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Long) = elements[i.toInt()].value
    fun cas(i: Long, expect: Any?, update: Any?) = elements[i.toInt()].compareAndSet(expect, update)
}
private class Dummy

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

