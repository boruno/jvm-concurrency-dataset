//package day2

import day1.*
import java.util.concurrent.atomic.*
import kotlin.math.max
import kotlin.math.min

class FAABasedQueue<E> : Queue<E> {
    private val head = AtomicReference(Segment(-1))
    private val tail = AtomicReference(head.get())

    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)

    private fun getSegmentAndCellIdx(start: Segment, idx: Long): Pair<Segment, Int> {
        val expectedSegId = idx / SEGMENT_SIZE
        assert(start.id <= expectedSegId)

        var cur = start
        while (cur.id != expectedSegId) {
            val next = cur.next.get()
            if (next != null) {
                cur = next
            } else {
                val newOne = Segment(cur.id + 1)
                if (cur.next.compareAndSet(null, newOne)) {
                    cur = newOne
                }
            }
        }

        val cellIdx = (idx % SEGMENT_SIZE).toInt()
        return Pair(cur, cellIdx)
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val curEnqIdx = enqIdx.getAndIncrement()
            val (seg, cellIdx) = getSegmentAndCellIdx(curTail, curEnqIdx)
            if (curTail !== seg) {
                tail.compareAndSet(curTail, seg)
            }
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
            if (curHead !== seg) {
                head.compareAndSet(curHead, seg)
            }
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

    override fun validate() {
        val (deqSeg, deqCellIdx) = getSegmentAndCellIdx(head.get(), deqIdx.get())
        check((deqCellIdx == 0 && deqSeg.id == 1 + tail.get().id) || (deqCellIdx > 0 && deqSeg.id == tail.get().id)) {
            "`deqIdx = ${deqIdx.get()}` points to segment with id ${deqSeg.id} but `head` points to segment with id ${head.get().id}"
        }

        val (enqSeg, enqCellIdx) = getSegmentAndCellIdx(tail.get(), enqIdx.get())
        check((enqCellIdx == 0 && enqSeg.id == 1 + tail.get().id) || (enqCellIdx > 0 && enqSeg.id == tail.get().id)) {
            "`enqIdx = ${enqIdx.get()}` points to segment with id ${enqSeg.id} but `tail` points to segment with id ${tail.get().id}"
        }

        val (lo, hi) =
            if (deqIdx.get() <= enqIdx.get()) {
                Pair(Pair(deqSeg, deqCellIdx), Pair(enqSeg, enqCellIdx))
            } else {
                Pair(Pair(enqSeg, enqCellIdx), Pair(deqSeg, deqCellIdx))
            }
        val (loSeg, loCellIdx) = lo
        val (hiSeg, hiCellIdx) = hi

        for (i in 0 until loCellIdx) {
            check(loSeg.cells[i] == null || loSeg.cells[i] == POISONED) {
                "`segment#${loSeg.id}.cells[$i]` must be `null` or `POISONED` with `deqIdx = ${deqIdx.get()}` and `enqIdx = ${enqIdx.get()}`"
            }
        }

        for (i in hiCellIdx until SEGMENT_SIZE) {
            check(hiSeg.cells[i] == null || hiSeg.cells[i] == POISONED) {
                "`segment#${hiSeg.id}.cells[$i]` must be `null` or `POISONED` with `deqIdx = ${deqIdx.get()}` and `enqIdx = ${enqIdx.get()}`"
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
