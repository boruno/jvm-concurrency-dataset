package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*
import kotlin.random.Random

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
        val cell = randomCellIndex()
        if (!eliminationArray[cell].compareAndSet(CELL_STATE_EMPTY, element)) return false
        for (i in 1..ELIMINATION_WAIT_CYCLES ) {
            if (eliminationArray[cell].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true
        }
        return !eliminationArray[cell].compareAndSet(element, CELL_STATE_EMPTY)
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val cell = randomCellIndex()
        val value = eliminationArray[cell].value
        if (value != CELL_STATE_EMPTY && value != CELL_STATE_RETRIEVED) {
            return if (eliminationArray[cell].compareAndSet(value, CELL_STATE_RETRIEVED)) {
                value as E?
            } else null
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
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}
