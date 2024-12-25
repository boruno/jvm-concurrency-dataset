//package day2

import day1.*
import java.util.concurrent.atomic.*

class FAABasedQueue<E> : Queue<E> {
    private val head = AtomicReference(Segment(0))
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

    private fun movePointer(pointer: AtomicReference<Segment>, newSeg: Segment) {
        val oldSeg = pointer.get()
        if (oldSeg.id < newSeg.id) {
            pointer.compareAndSet(oldSeg, newSeg)
        }
    }

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail.get()
            val curEnqIdx = enqIdx.getAndIncrement()
            val (seg, cellIdx) = getSegmentAndCellIdx(curTail, curEnqIdx)
//            movePointer(tail, seg)
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
            movePointer(head, seg)
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
        fun checkIdxAndPointerAreNotFar(pointerName: String, pointer: AtomicReference<Segment>,
                                        idxName: String, idx: AtomicLong) {
            val (seg, _) = getSegmentAndCellIdx(pointer.get(), idx.get())
            check(seg.id - pointer.get().id in 0..1) {
                "`${pointerName}` must point to segment with id ${seg.id - 1} or ${seg.id} but points to segment with id ${pointer.get().id} and `${idxName} = ${idx.get()}`"
            }
        }

        checkIdxAndPointerAreNotFar("head", head, "deqIdx", deqIdx)
        checkIdxAndPointerAreNotFar("tail", tail, "enqIdx", enqIdx)

        val deqInfo = getSegmentAndCellIdx(head.get(), deqIdx.get())
        val enqInfo = getSegmentAndCellIdx(tail.get(), enqIdx.get())
        val (lo, hi) =
            (if (deqIdx.get() <= enqIdx.get()) Pair(deqInfo, enqInfo) else Pair(enqInfo, deqInfo))
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
