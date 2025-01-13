//package day2

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray

class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    private val head = AtomicReference(Segment(0))
    private val tail = AtomicReference(head.get())

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

            tail.compareAndSet(localTail, localTail.next.get()!!)
        }
    }

    private fun moveHeadForward(start: Segment) {
        while (true) {
            val localHead = head.get()
            val localDeqIdx = deqIdx.get()

            if (localHead.id == localDeqIdx % 64 || localHead.next.get() == null) {
                return
            }

            head.compareAndSet(localHead, localHead.next.get()!!)
        }
    }

    private fun findSegment(start: Segment, i: Long) : Segment {
        var current = start
        while (true) {
            if (current.id == i) {
                return current
            }
            current.next.compareAndSet(null, Segment(current.id + 1))
            current = current.next.get()!!
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            val localTail = tail.get()
            val i = enqIdx.getAndIncrement()
            val segment = findSegment(localTail, i % 64)
            moveTailForward(segment)
            if (segment.array.compareAndSet((i % 64).toInt(), null, element)) {
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
            val i = deqIdx.getAndIncrement()
            val segment = findSegment(localHead, i % 64)
            moveHeadForward(segment)

            if (segment.array.compareAndSet((i % 64).toInt(), null, POISONED)) {
                continue
            }

            return segment.array.get((i % 64).toInt()) as E?
        }
    }

    class Segment(val id: Long) {
        val array = AtomicReferenceArray<Any?>(64)
        val next = AtomicReference<Segment?>(null)
    }
}

private val POISONED = Any()