package day2

import day1.*
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {

    private var head = Segment(0)
    private var tail = head
    private var enqIdx = AtomicLong(0)
    private var deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail
            val i = enqIdx.getAndIncrement()
            val curSegment = findSegment(curTail, i / SEGMENT_SIZE)
            moveTailForward(curSegment)
            if (curSegment.cells.compareAndSet(i.toInt() % SEGMENT_SIZE, null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!canDequeue()) return null
            val curHead = head
            val i = deqIdx.getAndIncrement()
            val curSegment = findSegment(curHead, i / SEGMENT_SIZE)
            moveHeadForward(curSegment)
            if (curSegment.cells.compareAndSet(i.toInt() % SEGMENT_SIZE, null, POISONED)) {
                continue
            }
            return curSegment.cells.getAndSet(i.toInt() % SEGMENT_SIZE, null) as E
        }
    }

    private fun canDequeue(): Boolean {
        while (true) {
            val enq = enqIdx.get()
            val deq = deqIdx.get()
            if (enq != enqIdx.get()) {
                continue
            }
            return deq < enq
        }
    }

    private fun findSegment(start: Segment, segmentIdx: Long): Segment {
        if (start.id == segmentIdx) return start
        // finding the most recent tail
        val current = start.next
        while (true) {
            val curSegment = current.get()
            // check if the segment with requested idx was already added
            if (curSegment != null && curSegment.id == segmentIdx) {
                return curSegment
            }
            // nope, trying to move forward
            val newSegment = Segment(segmentIdx)
            // if current holds null, it means no one has created the segment with requested idx yet
            if (current.compareAndSet(null, newSegment)) {
                // this is our chance!
                return newSegment
            }
            // nope, progress
            current.compareAndSet(curSegment, curSegment?.next?.get())
        }
    }

    private fun moveTailForward(to: Segment) {
        if (tail.id >= to.id) return
        tail = to
    }

    private fun moveHeadForward(to: Segment) {
        if (head.id >= to.id) return
        head = to
    }
}

private class Segment(val id: Long) {
    val next = AtomicReference<Segment?>(null)
    val cells = AtomicReferenceArray<Any?>(SEGMENT_SIZE)
}

// DO NOT CHANGE THIS CONSTANT
private const val SEGMENT_SIZE = 2

// TODO: poison cells with this value.
private val POISONED = Any()