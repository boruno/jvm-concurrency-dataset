package mpp.faaqueue

import kotlinx.atomicfu.*

class FAAQueue<E> {
    private val BROKEN = Any()
    private val head: AtomicRef<Segment> // Head pointer, similarly to the Michael-Scott queue (but the first node is _not_ sentinel)
    private val tail: AtomicRef<Segment> // Tail pointer, similarly to the Michael-Scott queue
    private val enqIdx = atomic(0L)
    private val deqIdx = atomic(0L)

    init {
        val firstNode = Segment(null, 0)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Adds the specified element [x] to the queue.
     */
    fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(
                start = curTail,
                id = i / SEGMENT_SIZE
            )
            if (s.id > curTail.id) {
                tail.compareAndSet(curTail, s)
            }
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, element)) {
                return
            }
        }
    }

    private fun findSegment(start: Segment, id: Long): Segment {
        var curSegment = start
        while (id > curSegment.id) {
            val newSegment = Segment(null, curSegment.id + 1)
            if (curSegment.next == null) {
                curSegment.next = newSegment
            }
            curSegment = newSegment
        }
        return curSegment
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) {
                return null
            }
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
            val s = findSegment(
                start = curHead,
                id = i / SEGMENT_SIZE
            )
            if (s.id > curHead.id) {
                tail.compareAndSet(curHead, s)
            }
            if (s.cas((i % SEGMENT_SIZE).toInt(), null, BROKEN)) {
                continue
            }
            return s.get((i % SEGMENT_SIZE).toInt()) as E
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

private class Segment(var next: Segment?, var id: Int) {
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

