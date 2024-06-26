package mpp.faaqueue

import kotlinx.atomicfu.*

data class Trash(var trash: Trash? = null) {}

class FAAQueue<E> {
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)
    private val dummy = Segment(-1)

    init {
        val firstNode = Segment(0)

        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    private fun moveTailForward(segment: Segment) {
        while (segment.id > tail.value.id) tail.compareAndSet(tail.value, tail.value.next.value!!)
    }

    private fun moveHeadForward(segment: Segment) {
        while (segment.id > head.value.id) head.compareAndSet(head.value, head.value.next.value!!)
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var s = start
        var i = s.id
        while (i < id / SEGMENT_SIZE) {
            var next = s.next.value
            if (next == null) {
                s.next.compareAndSet(null, Segment(i + 1))
                next = s.next.value
            }
            if (next != null) {
                s = next
            }
            i++
        }
        return s
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndAdd(1)
            val segment = findSegment(curTail, i.toInt())
            moveTailForward(segment)
            if (segment.cas((i % SEGMENT_SIZE).toInt(), null, element)) return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value <= enqIdx.value) return null
            val curHead = head.value
            val i = enqIdx.getAndAdd(1)
            val segment = findSegment(curHead, i.toInt())
            moveHeadForward(segment)
            if (segment.cas((i % SEGMENT_SIZE).toInt(), null, Trash())) continue
            return segment.get((i % SEGMENT_SIZE).toInt()) as E
        }
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            return deqIdx.value <= enqIdx.value
        }
}

private class Segment(var id: Int) {
    val next: AtomicRef<Segment?> = atomic(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    private fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

