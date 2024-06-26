package day2

import day1.*
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {

    private var head = Segment(0)
    private var tail = head
    private var enqIdx = AtomicLong(0)
    private var deqIdx = AtomicLong(0)

    override fun enqueue(element: E) {
        while (true) {
            val curTail = tail
            val i = enqIdx.getAndIncrement()
            val curSegment = findSegment(curTail, i / SEGMENT_SIZE)
            moveTailForward(curSegment)
            if (curSegment.cells.compareAndSet(i.toInt() % SEGMENT_SIZE, null, element)) {
                return
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            // Is this queue empty?
            if (!canDequeue()) return null
            val curHead = head
            val i = deqIdx.getAndIncrement()
            val curSegment = findSegment(curHead, i / SEGMENT_SIZE)
            moveHeadForward(curSegment)
            if (curSegment.cells.compareAndSet(i.toInt() % SEGMENT_SIZE, null, POISONED)) {
                continue
            }
            return curSegment.cells.getAndSet(i.toInt() % SEGMENT_SIZE, null) as E
        }
    }

    private fun canDequeue(): Boolean {
        while (true) {
            val enq = enqIdx.get()
            val deq = deqIdx.get()
            if (enq != enqIdx.get()) {
                continue
            }
            return deq < enq
        }
    }

    private fun findSegment(start: Segment, segmentIdx: Long): Segment {
        val difference = segmentIdx - start.id
        var current = start
        // progress to segmentIdx, probably creating intermediate segments
        // (they can be not created yet)
        for (i in 1..difference) {
            val newSegment = Segment(start.id + i)
            if (current.next.compareAndSet(null, newSegment)) {
                current = newSegment
            } else {
                current = current.next.get()
                        // if there's null, it means someone has invalidated the whole string after CAS
                        // => the segment has already been invalidates for us, so it shouldn't matter what we return here
                    ?: newSegment
            }
        }
        return current
    }

    private fun moveTailForward(to: Segment) {
        if (tail.id >= to.id) return
        tail = to
    }

    private fun moveHeadForward(to: Segment) {
        if (head.id >= to.id) return
        head = to
    }

    override fun validate() {

        fun checkIdxAndPointerAreNotFar(
            pointerName: String, seg: Segment,
            idxName: String, idx: AtomicLong
        ): Segment? {
            val expectedSegId = idx.get() / SEGMENT_SIZE
            return when (expectedSegId) {
                seg.id -> seg

                // there are tricky cases on boundaries, allow them
                seg.id + 1 -> seg.next.get()

                else -> throw IllegalStateException(
                    "`${pointerName}` must point to segment with id ${expectedSegId - 1} or $expectedSegId " +
                            "but points to segment with id ${seg.id} and `${idxName} = ${idx.get()}`"
                )
            }
        }

        val deqSeg = checkIdxAndPointerAreNotFar("head", head, "deqIdx", deqIdx)
        val enqSeg = checkIdxAndPointerAreNotFar("tail", tail, "enqIdx", enqIdx)

        val deqInfo = Pair(deqSeg, deqIdx.get() % SEGMENT_SIZE)
        val enqInfo = Pair(enqSeg, enqIdx.get() % SEGMENT_SIZE)
        val (lo, hi) =
            (if (deqIdx.get() <= enqIdx.get()) Pair(deqInfo, enqInfo) else Pair(enqInfo, deqInfo))
        val (loSeg, loCellIdx) = lo
        val (hiSeg, hiCellIdx) = hi

        for (i in 0 until loCellIdx.toInt()) {
            val loSeg = loSeg!!
            check(loSeg.cells[i] == null || loSeg.cells[i] == POISONED) {
                "`segment#${loSeg.id}.cells[$i]` must be `null` or `POISONED` with `deqIdx = ${deqIdx.get()}` and `enqIdx = ${enqIdx.get()}`"
            }
        }

        for (i in hiCellIdx.toInt() until SEGMENT_SIZE) {
            if (hiSeg == null) break
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

// TODO: poison cells with this value.
private val POISONED = Any()