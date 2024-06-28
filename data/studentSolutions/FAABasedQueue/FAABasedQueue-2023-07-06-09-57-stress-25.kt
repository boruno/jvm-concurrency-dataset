package day2

import day1.Queue
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.
private val POISONED = Any()
private val SEGMENT_SIZE = 1

@Suppress("KotlinConstantConditions")
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
            val currTail = tail
            val i = enqIdx.getAndIncrement()
            val segment = findOrCreateSegment(currTail.value, i / SEGMENT_SIZE)
            moveTailForward(segment)
            if (segment.array[i % SEGMENT_SIZE].compareAndSet(null, element)) return
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (shouldNotTryDeque()) return null
            val curHead = head
            val i = deqIdx.getAndIncrement()
            val segment = findOrCreateSegment(curHead.value, i / SEGMENT_SIZE)
            moveHeadForward(segment)
            if (segment.array[i % SEGMENT_SIZE].compareAndSet(null, POISONED)) continue
            return segment.array[i % SEGMENT_SIZE].value as E
        }
    }

    fun shouldNotTryDeque(): Boolean {
        while (true) {
            val curDeqInx = deqIdx.value
            val curEnqInx = enqIdx.value
            if (curDeqInx != deqIdx.value) continue
            return curDeqInx >= curEnqInx
        }
    }

    private fun findOrCreateSegment(start: Segment, id: Int): Segment {
        var currSegment = start
        while (true) {
            if (currSegment.id == id) return currSegment
            val nextSegment = currSegment.next.value
            if (nextSegment == null) {
                currSegment.next.compareAndSet(null, Segment(id + 1))
                return currSegment.next.value!!
            }
            currSegment = nextSegment
        }
    }

    private fun moveHeadForward(segment: Segment) {
        val curHead = head.value
        if (curHead.id < segment.id) {
            head.compareAndSet(curHead, segment)
        }
    }

    private fun moveTailForward(segment: Segment) {
        val curTail = tail.value
        if (curTail.id < segment.id) {
            head.compareAndSet(curTail, segment)
        }

    }

    private inner class Segment(
        val id: Int
    ) {
        val array = atomicArrayOfNulls<Any?>(SEGMENT_SIZE)
        val next = atomic<Segment?>(null)
    }
}

