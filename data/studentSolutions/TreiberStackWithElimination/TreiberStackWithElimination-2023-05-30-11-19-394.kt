//package day1

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
        val cellIndex = randomCellIndex()

        // Try to install the element in the cell
        if (eliminationArray[cellIndex].compareAndSet(CELL_STATE_EMPTY, element)) {

            // Wait ELIMINATION_WAIT_CYCLES loop cycles
            repeat(ELIMINATION_WAIT_CYCLES) { }

            // Check if a concurrent pop() grabbed the element
            return if (eliminationArray[cellIndex].compareAndSet(element, CELL_STATE_EMPTY)) {
                false // Pop didn't retrieve the value
            } else {
                eliminationArray[cellIndex].value = CELL_STATE_EMPTY
                true // Pop retrieved the value
            }
        }

        return false // Failed to install the element in the cell
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val cellIndex = randomCellIndex()
        val cellValue = eliminationArray[cellIndex].value

        // Check if the cell has an element
        if (cellValue != CELL_STATE_EMPTY && cellValue != CELL_STATE_RETRIEVED) {
            // Retrieve the element and set cell state to retrieved
            if (eliminationArray[cellIndex].compareAndSet(cellValue, CELL_STATE_RETRIEVED)) {
                @Suppress("UNCHECKED_CAST")
                return cellValue as E // Return the element
            }
        }

        return null // Cell is empty or couldn't retrieve the element
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