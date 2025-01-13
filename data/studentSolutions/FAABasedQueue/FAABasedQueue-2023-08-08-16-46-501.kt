//package day2

import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {
    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>

    init {
        val dummy = Segment(0L)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val enqueIdx = enqIdx.getAndIncrement()
            val segment = findSegment(curTail, enqueIdx / SEGMENT_SIZE)
            moveTailForward(segment)

            if (segment.cells.compareAndSet(enqueIdx.toInt() % SEGMENT_SIZE, null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryToDequeue()) {
                return null
            }

            val curHead = head.get()
            val dequeIdx = deqIdx.getAndIncrement()
            val segment = findSegment(curHead, dequeIdx / SEGMENT_SIZE)
            moveHeadForward(segment)

            if (!segment.cells.compareAndSet(dequeIdx.toInt() % SEGMENT_SIZE, null, POISONED)) {
                try {
                    return segment.cells.get(dequeIdx.toInt() % SEGMENT_SIZE) as E?
                } finally {
                    segment.cells.set(dequeIdx.toInt() % SEGMENT_SIZE, null)
                }
            }
        }
    }

    private fun moveTailForward(s: Segment) {
        val curTail = tail.get()

        if (s.id > curTail.id) {
            tail.compareAndSet(curTail, s)
        }
    }

    private fun moveHeadForward(s: Segment) {
        val curHead = head.get()

        if (s.id > curHead.id) {
            head.compareAndSet(curHead, s)
        }
    }

    private fun findSegment(start: Segment, idx: Long): Segment {
        var current = start
        while (true) {
            if (current.id == idx) {
                return current
            }

            val next = current.next.get()
            if (next != null) {
                current = next
            } else {
                tryAddSegment(current)
            }
        }
    }

    private fun tryAddSegment(last: Segment) {
        val newSegment = Segment(id = last.id + 1)
        if (last.next.compareAndSet(null, newSegment)) {
            tail.compareAndSet(last, newSegment)
            return
        } else {
            tail.compareAndSet(last, last.next.get())
            return
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while (true) {
            val deqIdxValue = deqIdx.get()
            val enqIdxValue = enqIdx.get()
            if (deqIdxValue == deqIdx.get()) {
                return deqIdxValue < enqIdxValue
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
