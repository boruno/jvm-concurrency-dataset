package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val idx = randomCellIndex()
        val cell = eliminationArray[idx]
        if (cell.compareAndSet(CELL_STATE_EMPTY, element)) {
            for (i in 1..ELIMINATION_WAIT_CYCLES) {
                if (cell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                    return true
                }
            }
            cell.value = CELL_STATE_EMPTY // todo
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val idx = randomCellIndex()
        val cell = eliminationArray[idx]
        val value = cell.value
        return if (value != CELL_STATE_EMPTY &&
            value != CELL_STATE_RETRIEVED &&
            cell.compareAndSet(value, CELL_STATE_RETRIEVED)
        ) {
            @Suppress("UNCHECKED_CAST")
            value as E
        } else {
            null
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}