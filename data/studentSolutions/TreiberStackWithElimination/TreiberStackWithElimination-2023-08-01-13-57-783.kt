package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val randomCellIndex = randomCellIndex()
        val cell = eliminationArray[randomCellIndex]
        if (cell.value == CELL_STATE_EMPTY) {
            cell.compareAndSet(null, element)
            var cycle = 0
            while (cycle < ELIMINATION_ARRAY_SIZE) {
                if (cell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true
                cycle += 1
            }
            cell.compareAndSet(element, CELL_STATE_EMPTY)
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val randomCellIndex = randomCellIndex()
        val cell = eliminationArray[randomCellIndex]
        if (cell.value != CELL_STATE_RETRIEVED && cell.value != null) {
            val ret = cell.value
            cell.compareAndSet(ret, CELL_STATE_RETRIEVED)
            return ret as? E
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