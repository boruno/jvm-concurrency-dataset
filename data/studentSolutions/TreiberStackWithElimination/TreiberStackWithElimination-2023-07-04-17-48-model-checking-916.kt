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
        if (!cell.compareAndSet(CELL_STATE_EMPTY, element)) {
            return false
        }
        for (i in 0..ELIMINATION_WAIT_CYCLES) {
            if (cell.value == CELL_STATE_RETRIEVED) {
                cell.value = CELL_STATE_EMPTY
                return true
            }
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val idx = randomCellIndex()
        val cell = eliminationArray[idx]
        val value = cell.value
        if (value != null && value != CELL_STATE_RETRIEVED) {
            if (cell.compareAndSet(value, CELL_STATE_RETRIEVED)) {
                return value as E
            }
        }
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

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