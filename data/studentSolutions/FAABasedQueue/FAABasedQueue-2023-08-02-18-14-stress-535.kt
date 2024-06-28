package day2

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
        val dummy = Segment()
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.value
            val i = enqIdx.getAndIncrement()
            val s = findSegment(curTail, i / SEGM_SIZE)
            moveTailForward(s)
            if (s.segment[i % SEGM_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }

    private fun moveTailForward(segment: Segment) {
        tail.compareAndSet(tail.value, segment)
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            val curHead = head.value
            val i = deqIdx.getAndIncrement()
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

    private fun moveHeadForward(segment: Segment) {
        head.compareAndSet(head.value, segment)
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var counter = 0
        var currSegment = start

        while (counter < id) {
            val next = currSegment.next.value
            if (next != null) {
                currSegment = next
            } else {
                val new = Segment()
                currSegment.next.value = new
                currSegment = new
            }
            counter++
        }

        return currSegment
    }

    inner class Segment {
        val segment = atomicArrayOfNulls<Any?>(SEGM_SIZE)
        val next = atomic<Segment?>(null)
    }
}

private const val SEGM_SIZE = 3
private val POISONED = Any()