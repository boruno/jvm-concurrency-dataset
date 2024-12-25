//package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>

    init {
        val dummy = Segment(0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val currentTail = tail.value
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = enqIdx.incrementAndGet()
            val segment = findSegment(currentTail, i)
            moveTailForward(segment)
            // TODO: Atomically install the element into the cell
            // TODO: if the cell is not poisoned.
            if (segment.array[i % SEGMENT_SIZE].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!shouldTryToDequeue()) return null
            val currentHead = head.value
            // TODO: Increment the counter atomically via Fetch-and-Add.
            // TODO: Use `getAndIncrement()` function for that.
            val i = deqIdx.incrementAndGet()
            val segment = findSegment(currentHead, i)
            moveHeadForward(segment)
            // TODO: Try to retrieve an element if the cell contains an
            // TODO: element, poisoning the cell if it is empty.
            if (segment.array[i % SEGMENT_SIZE].compareAndSet(null, POISONED)) continue
            return segment.array[i % SEGMENT_SIZE].value as E
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val deq = deqIdx.value
            val enq = enqIdx.value
            if (deq != deqIdx.value) continue
            return deq < enq
        }
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var normalizedId = id
        var segment: Segment = start
        var segmentId = start.id
        while (id >= SEGMENT_SIZE) {
            segment = segment.next.value ?: break
            normalizedId -= SEGMENT_SIZE
            segmentId++
        }
        if (id <= SEGMENT_SIZE) return segment // found required segment
        assert(normalizedId - SEGMENT_SIZE < SEGMENT_SIZE)
        val newSegment = Segment(segmentId + 1)
        return if (segment.next.compareAndSet(null, newSegment)) {
            newSegment
        } else {
            segment.next.value!!
        }
    }

    private fun moveTailForward(segment: Segment) {
        if (tail.value.id < segment.id) {
            tail.value = segment
        }
    }

    private fun moveHeadForward(segment: Segment) {
        if (head.value.id < segment.id) {
            head.value = segment
        }
    }

    private class Segment(val id: Int) {
        val array: AtomicArray<Any?> = atomicArrayOfNulls(SEGMENT_SIZE)
        val next = atomic<Segment?>(null)
    }
}

private val POISONED = Any()
private const val SEGMENT_SIZE = 10