//package mpp.faaqueue

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
            val idx = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, idx / SEGMENT_SIZE)
            moveTailForward(segment)

            if (segment.cas((idx % SEGMENT_SIZE).toInt(), null, element))
                return
        }
    }

    /**
     * Retrieves the first element from the queue and returns it;
     * returns `null` if the queue is empty.
     */
    fun dequeue(): E? {
        while (!isEmpty) {
            val curHead = head.value
            val idx = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, idx / SEGMENT_SIZE)
            moveHeadForward(segment)

            val elemIdx = (idx % SEGMENT_SIZE).toInt();
            if (segment.cas(elemIdx, null, Bottom))
                continue
            @Suppress("UNCHECKED_CAST")
            return segment.get(elemIdx) as E
        }
        return null
    }

    /**
     * Returns `true` if this queue is empty, or `false` otherwise.
     */
    val isEmpty: Boolean
        get() {
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            return curDeqIdx <= curEnqIdx
        }

    object Bottom

    companion object {
        private fun findSegment(start: Segment, id: Long): Segment {
            var current = start
            while (current.id != id) {
                val newNext = Segment(current.id + 1)
                current.next.compareAndSet(null, newNext)
                current = current.next.value!!
            }
            return current
        }
    }

    private fun moveTailForward(next: Segment) {
        while (true) {
            val curTail = tail.value
            if (curTail.id >= next.id) break
            tail.compareAndSet(curTail, next)
        }
    }

    private fun moveHeadForward(next: Segment) {
        while (true) {
            val curHead = head.value
            if (curHead.id >= next.id) break
            head.compareAndSet(curHead, next)
        }
    }
}

private class Segment(val id: Long) {
    val next = atomic<Segment?>(null)
    val elements = atomicArrayOfNulls<Any>(SEGMENT_SIZE)

    fun get(i: Int) = elements[i].value
    fun cas(i: Int, expect: Any?, update: Any?) = elements[i].compareAndSet(expect, update)
    fun put(i: Int, value: Any?) {
        elements[i].value = value
    }
}

const val SEGMENT_SIZE = 2 // DO NOT CHANGE, IMPORTANT FOR TESTS

