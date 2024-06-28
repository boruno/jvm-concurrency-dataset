package day1

import kotlinx.atomicfu.*
import java.util.concurrent.ThreadLocalRandom

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
        val index = randomCellIndex()
        val cell = eliminationArray[index]
        if (cell.compareAndSet(CELL_STATE_EMPTY, element)) {
            for (i in 0 until ELIMINATION_WAIT_CYCLES) {
                if (cell.compareAndSet(element, CELL_STATE_EMPTY)) {
                    return true
                }
                Thread.yield()
            }
            cell.compareAndSet(element, CELL_STATE_EMPTY)
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val cell = eliminationArray[index]
        val value = cell.value
        if (value != CELL_STATE_EMPTY && cell.compareAndSet(value, CELL_STATE_EMPTY)) {
            return value as E
        }
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains an element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}
