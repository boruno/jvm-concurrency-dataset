package day2

import day1.*
import java.util.concurrent.atomic.*

class FAABasedQueue<E> : Queue<E> {
    private val head = AtomicReference(Segment(0))
    private val tail = AtomicReference(head.get())

    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    private fun getSegmentAndCellIdx(start: Segment, idx: Long): Pair<Segment, Int> {
        val segId = idx / SEGMENT_SIZE

        val cellIdx = (idx % SEGMENT_SIZE).toInt()
        return Pair(start, cellIdx)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val curEnqIdx = enqIdx.getAndIncrement()
            val (seg, cellIdx) = getSegmentAndCellIdx(curTail, curEnqIdx)
            if (seg.cells.compareAndSet(cellIdx, null, element)) {
                break
            }
        }
    }

    override fun dequeue(): E? {
        while (true) {
            if (canAssumeEmptiness()) return null
            val curHead = head.get()
            val curDeqIdx = deqIdx.getAndIncrement()
            val (seg, cellIdx) = getSegmentAndCellIdx(curHead, curDeqIdx)
            val element = seg.cells.getAndSet(cellIdx, POISONED)
            if (element != null) {
                @Suppress("UNCHECKED_CAST")
                return element as E
            }
        }
    }

    private fun canAssumeEmptiness(): Boolean {
        while (true) {
            val curEnqIdx = enqIdx.get()
            val curDeqIdx = deqIdx.get()
            if (curEnqIdx != enqIdx.get()) {
                continue
            }
            return curDeqIdx >= curEnqIdx
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
