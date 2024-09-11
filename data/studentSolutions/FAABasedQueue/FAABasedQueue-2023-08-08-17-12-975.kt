package day2

import day1.*
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    private val head = AtomicReference(Segment(0))
    private val tail = AtomicReference(head.get())

    override fun enqueue(element: E) {
        while (true) {
            val localTail = tail.get()
            val i = enqIdx.getAndIncrement().toInt()
            val segment = findSegment(localTail, i % 64)
            moveTailForward(segment)
            if (segment.cells.compareAndSet(i % 64, null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryToDeque()) {
                return null
            }

            val localHead = head.get()
            val i = deqIdx.getAndIncrement().toInt()
            val segment = findSegment(localHead, i % 64)
            moveHeadForward(segment)

            if (segment.cells.compareAndSet(i % 64, null, POISONED)) {
                continue
            }

            return segment.cells.get(i % 64) as E?
        }
    }
    private fun shouldTryToDeque(): Boolean {
        while (true) {
            val curDeqIdx = deqIdx.get()
            val curEnqIdx = enqIdx.get()
            if (curDeqIdx != deqIdx.get()) {
                continue
            }
            return curDeqIdx <= curEnqIdx
        }
    }

    private fun moveTailForward(start: Segment) {
        while (true) {
            val localTail = tail.get()
            val localEnqIdx = enqIdx.get()

            if (localTail.id == localEnqIdx % 64 || localTail.next.get() == null) {
                return
            }

            tail.compareAndSet(localTail, localTail.next.get())
        }
    }

    private fun moveHeadForward(start: Segment) {
        while (true) {
            val localHead = head.get()
            val localDeqIdx = deqIdx.get()

            if (localHead.id == localDeqIdx % 64 || localHead.next.get() == null) {
                return
            }

            head.compareAndSet(localHead, localHead.next.get())
        }
    }

    private fun findSegment(start: Segment, i: Int) : Segment {
        var current = start
        while (true) {
            if (current.id == i.toLong()) {
                return current
            }
            current.next.compareAndSet(null, Segment(current.id + 1))
            current = current.next.get()!!
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