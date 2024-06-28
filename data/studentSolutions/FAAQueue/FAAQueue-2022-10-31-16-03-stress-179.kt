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
            val i = enqIdx.getAndIncrement()
            val seg = findSegment(currentTail, i / SEGMENT_SIZE)
            tail.compareAndSet(currentTail, seg)
            if (seg.cas(((i % SEGMENT_SIZE).toInt()), null, element)) {
                return
            }
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (!isEmpty) {
            val currentHead = head.value
            val i = deqIdx.getAndIncrement()
            val seg = findSegment(currentHead, i / SEGMENT_SIZE)
            head.compareAndSet(currentHead, seg)
            if (seg.cas((i % SEGMENT_SIZE).toInt(), null, State.BROKEN)) {
                continue
            }
            return seg.get((i % SEGMENT_SIZE).toInt()) as E?
        }
        return null
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value
        }

    private fun findSegment(current: Segment, id: Long): Segment {
        var segment = current
        //println("id: " + id)
        while (segment.id < id) {
            while (segment.next.value == null) {
                segment.next.compareAndSet(null, Segment(segment.id + 1))
            }
            //println(segment.id)
            segment = segment.next.value!!
        }
        return segment
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

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

enum class State {
    BROKEN
}