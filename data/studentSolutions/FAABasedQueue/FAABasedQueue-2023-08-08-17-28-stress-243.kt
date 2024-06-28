package day2

import day1.Queue
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {

    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>

    init {
        val dummy = Segment(-1)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val i = enqIdx.getAndIncrement()
            val segId = i / SEGMENT_SIZE
            val segOffset = (i % SEGMENT_SIZE).toInt()
            val segment = findSegment(curTail, segId)
            moveTailForward(segment)
            if (segment.cells.compareAndSet(segOffset, null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (deqIdx.get() >= enqIdx.get()) {
                return null
            }
            val curHead = head.get()
            val i = deqIdx.getAndIncrement()
            val segId = i / SEGMENT_SIZE
            val segOffset = (i % SEGMENT_SIZE).toInt()
            val segment = findSegment(curHead, segId)
            moveHeadForward(segment)
            if (segment.cells.compareAndSet(segOffset, null, POISONED)) {
                continue
            }
            val result = segment.cells.get(i.toInt()) as E
            segment.cells.set(i.toInt(), null)
            return result
        }
    }

    private fun findSegment(start: Segment, segId: Long): Segment {
        var currentSegment = start
        while (true) {
            if (currentSegment.id == segId) {
                return currentSegment
            }
            val nextSegment = currentSegment.next.get()
            if (nextSegment == null) {
                currentSegment.next.compareAndSet(null, Segment(currentSegment.id + 1))
            } else {
                currentSegment = nextSegment
            }
        }
    }

    private fun moveTailForward(segment: Segment) {
        while (true) {
            val curTail = tail.get()
            if (segment.id > curTail.id) {
                tail.compareAndSet(curTail, segment)
            } else {
                return
            }
        }
    }

    private fun moveHeadForward(segment: Segment) {
        while (true) {
            val curHead = head.get()
            if (segment.id > curHead.id) {
                head.compareAndSet(curHead, segment)
            } else {
                return
            }
        }
    }


}


private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

private val POISONED = Any()
