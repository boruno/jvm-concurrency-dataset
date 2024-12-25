//package day1

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
        repeat(ELIMINATION_WAIT_CYCLES) {
            val cellIndex = randomCellIndex()
            val cellRef = eliminationArray[cellIndex]
            if (cellRef.compareAndSet(null, element)) {
                repeat(ELIMINATION_WAIT_CYCLES) {
                    if (cellRef.compareAndSet(CELL_STATE_RETRIEVED, null)) {
                        return true
                    }
                }
                return false
            }
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        repeat(ELIMINATION_WAIT_CYCLES) {
            val cellIndex = randomCellIndex()
            val cellRef = eliminationArray[cellIndex]
            val currentValue = cellRef.value
            if (currentValue != null
                && currentValue != CELL_STATE_RETRIEVED
                && cellRef.compareAndSet(currentValue, CELL_STATE_RETRIEVED)) {
                return currentValue as E?
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