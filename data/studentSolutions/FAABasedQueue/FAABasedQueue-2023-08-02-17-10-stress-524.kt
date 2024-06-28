package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    private val head: AtomicRef<Segment<Any?>>
    private val tail: AtomicRef<Segment<Any?>>

    init {
        val dummy = Segment<Any?>()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.incrementAndGet()
            val s = findSegment(curTail, i / SEGM_SIZE)
            moveTailForward(s)
            if (s.segment[i % SEGM_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    private fun moveTailForward(segment: Segment<Any?>) {
        tail.compareAndSet(tail.value, segment)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (!shouldTryToDequeue()) return null
            val curHead = head.value
            val i = deqIdx.incrementAndGet()
            val s = findSegment(curHead, i / SEGM_SIZE)
            moveHeadForward(s)
            if (!s.segment[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                return s.segment[i % SEGM_SIZE].value as E
            }
        }
    }

    private fun shouldTryToDequeue(): Boolean {
        while(true) {
            val curDeqIdx = deqIdx.value
            val curEnqIdx = enqIdx.value
            if (curDeqIdx != deqIdx.value) continue
            return curDeqIdx >= curEnqIdx
        }
    }

    private fun moveHeadForward(segment: Segment<Any?>) {
        head.compareAndSet(head.value, segment)
    }

    private fun findSegment(start: Segment<Any?>, id: Int): Segment<Any?> {
        var counter = 0
        var currSegment = start

        while (counter < id) {
            currSegment = currSegment.next.value ?: Segment()
        }

        return currSegment
    }

    inner class Segment<E> {
        val segment = atomicArrayOfNulls<E>(SEGM_SIZE)
        val next = atomic<Segment<E>?>(null)
    }
}

private const val SEGM_SIZE = 3
private val POISONED = Any()