//package day2

import Queue
import java.util.concurrent.atomic.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size `Segment`s.
class FAABasedQueue<E> : Queue<E> {

    private val enqIdx = AtomicLong(0)
    private val deqIdx = AtomicLong(0)
    private val head: AtomicReference<Segment>
    private val tail: AtomicReference<Segment>

    override fun validate() {
        fun checkIdxAndPointerAreNotFar(pointerName: String, pointer: AtomicReference<Segment>,
                                        idxName: String, idx: AtomicLong): Segment? {
            val expectedSegId = idx.get() / SEGMENT_SIZE
            val seg = pointer.get()
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

    init {
        val dummy = Segment(-1)
        head = AtomicReference(dummy)
        tail = AtomicReference(dummy)
    }

    override fun enqueue(element: E) {
        while (true) {
            val enqIndex = enqIdx.getAndIncrement()
            val s = findSegment(enqIndex / SEGMENT_SIZE)
            if (s.cells.compareAndSet((enqIndex % SEGMENT_SIZE).toInt(), null, element)) return
        }
    }

    private fun findSegment(index: Long): Segment {
        var node = head.get()
        var lastIndex: Long
        while (true) {
            val curTail = tail.get()
            if (node.id == index) return node
            lastIndex = node.id
            val next = node.next
            val newSegment = Segment(++lastIndex)
            if (next.compareAndSet(null, newSegment)) {
                tail.compareAndSet(curTail, newSegment)
            }
            node = next.get()!!
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        while (true) {
            if (deqIdx.get() >= enqIdx.get()) return null
            val deqIndex = deqIdx.getAndIncrement()
            val s = findSegment(deqIndex / SEGMENT_SIZE)
            val indexByModulo = (deqIndex % SEGMENT_SIZE).toInt()
            if (s.cells.compareAndSet(indexByModulo, null, POISONED)) continue
            val res = s.cells.get(indexByModulo) as E
            s.cells.set(indexByModulo, null)
            return res
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
