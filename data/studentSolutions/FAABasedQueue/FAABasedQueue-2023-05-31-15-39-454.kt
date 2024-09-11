package day2

import day1.*
import kotlinx.atomicfu.*

// TODO: Copy the code from `FAABasedQueueSimplified`
// TODO: and implement the infinite array on a linked list
// TODO: of fixed-size segments.

private const val CELL_SIZE = 64
private val POISONED = Any()

class FAABasedQueue<E> : Queue<E> {

    private class Cell(
        val cellIndex: Int,
        val prev: Cell?
    ) {
        val infiniteArray = atomicArrayOfNulls<Any?>(CELL_SIZE)
        var next: Cell? = null
            get() {
                if (field == null) field = Cell(cellIndex + 1, this)
                return field
            }

        val minIdx = cellIndex * CELL_SIZE
        val maxIdx = (cellIndex + 1) * CELL_SIZE - 1
        fun updateCell(index: Int) : Cell? {
            if (index < minIdx) return prev
            if (index > maxIdx) return next
            return this
        }
    }

    private var tailCell = Cell(0, null)
        get() {
            if (tailIdx.value > field.maxIdx) field = field.next!!
            return field
        }
    private val tailIdx = atomic(0)
    private var headCell = tailCell
        get() {
            if (headIdx.value > field.maxIdx) field = field.next!!
            return field
        }
    private val headIdx = atomic(0)


    override fun enqueue(element: E) {
        do {
            val currTailCell = tailCell
            val currTailIdx = tailIdx.getAndIncrement()
            val localTailIdx = currTailIdx % CELL_SIZE
            val node = currTailCell.updateCell(currTailIdx)!!.infiniteArray[localTailIdx]
        } while (!node.compareAndSet(null, element))
    }

    @Suppress("UNCHECKED_CAST")
    override fun dequeue(): E? {
        var res: E?
        do {
            val headIdxPic = headIdx.value
            val tailIdxPic = tailIdx.value
            val headIdxPic2 = headIdx.value
            if (headIdxPic == headIdxPic2 && tailIdxPic < headIdxPic) return null
            val currHeadCell = headCell
            val currHeadIdx = headIdx.getAndIncrement()
            val localHeadIdx = currHeadIdx % CELL_SIZE
            val node = currHeadCell.updateCell(currHeadIdx)!!.infiniteArray[localHeadIdx]
            res = node.value as E?
        } while (node.compareAndSet(null, POISONED))
        return res
    }


}