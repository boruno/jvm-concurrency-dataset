//package day2

import kotlinx.atomicfu.*

private val POISONED = Any()

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)


   private val dummy = Segment<E>(0)
    private val head = atomic(dummy)
    private val tail = atomic(dummy)
    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value

            val i = enqIdx.getAndIncrement()

            val segment = findSegment(curTail, i / Segment.SEGMENT_SIZE)

//            moveTailForward()

            if (segment.array[i % Segment.SEGMENT_SIZE].compareAndSet(null, element))
                return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            val l = deqIdx.value
            val r = enqIdx.value
            if (r <= l) return null

            val curHead = head.value

            val i = deqIdx.getAndIncrement()

            val segment = findSegment(curHead, i / Segment.SEGMENT_SIZE)

            if (segment.array[i % Segment.SEGMENT_SIZE].compareAndSet(null, POISONED))
                continue
            return segment.array[i % Segment.SEGMENT_SIZE].value as E
        }
    }

    private fun findSegment(segment: Segment<E>, segmentId: Int): Segment<E> {
        while (segment.id != segmentId) {
            segment.next.compareAndSet(null, Segment(segment.id + 1))
        }

        return segment
    }

    private fun moveTailForward() {
        while(true) {
            val curTail = tail.value
            if (curTail.next.value == null) {
                return
            }
            tail.compareAndSet(curTail, curTail.next.value!!)
        }
    }

    private class Segment<E>(val id: Int) {
        val array = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)

        val next = atomic<Segment<E>?>(null)
        companion object {
            const val SEGMENT_SIZE = 8
        }
    }
}