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
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to install the element there.
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.
        val randomCellIndex = randomCellIndex()
        val cell = eliminationArray[randomCellIndex]
        if (cell.compareAndSet(CELL_STATE_EMPTY, element)) {
            repeat(ELIMINATION_WAIT_CYCLES) {
                if (cell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY))
                    return true
            }
            cell.update { value ->
                when(value) {
                    element, CELL_STATE_RETRIEVED -> CELL_STATE_EMPTY
                    else -> value
                }
            }
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.
        val randomCellIndex = randomCellIndex()
        val cell = eliminationArray[randomCellIndex]
        val value = cell.getAndUpdate { value ->
            when (value) {
                CELL_STATE_EMPTY, CELL_STATE_RETRIEVED -> value
                else -> CELL_STATE_RETRIEVED
            }
        }
        return when(value) {
            CELL_STATE_EMPTY, CELL_STATE_RETRIEVED -> null
            else -> value as E
        }
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