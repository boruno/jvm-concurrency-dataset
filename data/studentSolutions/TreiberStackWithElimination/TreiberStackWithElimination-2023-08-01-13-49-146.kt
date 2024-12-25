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
        // Choose a random cell in `eliminationArray`
        // and try to install the element there.
        val cellIndex = randomCellIndex()
        val cell = eliminationArray[cellIndex]
        if (!cell.compareAndSet(CELL_STATE_EMPTY, element))
            return false

        // Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // in hope that a concurrent `pop()` grabs the
        // element. If so, clean the cell and finish,
        // returning `true`. Otherwise, move the cell
        // to the empty state and return `false`.
        var numAttempt = 0
        while (numAttempt < ELIMINATION_WAIT_CYCLES) {
            if (cell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY))
                return true
            numAttempt++
        }
        cell.compareAndSet(element, CELL_STATE_EMPTY)
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // Choose a random cell in `eliminationArray`
        // and try to retrieve an element from there.
        // On success, return the element.
        // Otherwise, if the cell is empty, return `null`.
        val cellIndex = randomCellIndex()
        val cell = eliminationArray[cellIndex]
        val value = cell.value
        if (value == CELL_STATE_EMPTY || value == CELL_STATE_RETRIEVED) return null
        return if (cell.compareAndSet(value, CELL_STATE_RETRIEVED))
            value as E
        else
            null
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