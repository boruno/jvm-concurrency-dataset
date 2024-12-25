//package day2

import day1.*
import kotlinx.atomicfu.*

private const val SEGM_SIZE = 4

// TODO: Copy the code from `FAABasedQueueSimplified` and implement the infinite array on a linked list of fixed-size segments.
class FAABasedQueue<E> : Queue<E> {
    private class Segment(val id: Int) {
        val segment = atomicArrayOfNulls<Any?>(SEGM_SIZE)
        val next = atomic<Segment?>(null)
    }

    private val head: AtomicRef<Segment>
    private val tail: AtomicRef<Segment>
    private val enqIdx = atomic(0)
    private val deqIdx = atomic(0)

    init {
        val dummy = Segment(0)
        head = atomic(dummy)
        tail = atomic(dummy)
    }

    private fun findSegment(start: Segment, id: Int): Segment {
        var curSegment: Segment = start
        while (true) {
            var nextSegment: Segment? = curSegment.next.value
            while (nextSegment != null) {
                if (curSegment.id == id) return curSegment
                curSegment = nextSegment
                nextSegment = curSegment.next.value
            }
            val newSegment = Segment(curSegment.id + 1)
            curSegment.next.compareAndSet(null, newSegment)
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            var curTail = tail.value
            var i = enqIdx.getAndIncrement()
            var segmentNode = findSegment(curTail, i / SEGM_SIZE)
            tail.compareAndSet(curTail, segmentNode)
            if (segmentNode.segment[i % SEGM_SIZE].compareAndSet(null, element)) {
                return
            }
        }
    }


    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        //TODO("Implement me!")
        while (true) {
            if (deqIdx.value >= enqIdx.value) return null
            var curHead = head.value
            var i = deqIdx.getAndIncrement()
            var segmentNode = findSegment(curHead, i / SEGM_SIZE)
            head.compareAndSet(curHead, segmentNode)
            if (segmentNode.segment[i % SEGM_SIZE].compareAndSet(null, POISONED)) {
                continue
            }
            return segmentNode.segment[i % SEGM_SIZE].value as E
        }
    }
}

private val POISONED = Any()