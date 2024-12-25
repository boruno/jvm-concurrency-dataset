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
        // TODO: Choose a random cell in `eliminationArray`
        val randomCellIndex = randomCellIndex()
        val cell = eliminationArray[randomCellIndex]

        // TODO: and try to install the element there.
        if (!cell.compareAndSet(CELL_STATE_EMPTY, element)) {
            return false // cell is taken, bad luck, skip optimisation
        }

        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        repeat(ELIMINATION_WAIT_CYCLES) {
            // TODO: in hope that a concurrent `pop()` grabs the element
            // TODO: If so, clean the cell and finish returning `true`.
            if (cell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                return true
            }
        }

        // TODO: Otherwise, move the cell to the empty state and return `false`.
        val replaced = cell.getAndSet(CELL_STATE_EMPTY)
        // check whether right before the very last set we got lucky
        // and some concurrent `pop()` grabbed our value
        return replaced == CELL_STATE_RETRIEVED
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO: Choose a random cell in `eliminationArray`
        val randomCellIndex = randomCellIndex()
        val cell = eliminationArray[randomCellIndex]

        // TODO: and try to retrieve an element from there.
        val current = cell.value
        if (current == CELL_STATE_EMPTY || current == CELL_STATE_RETRIEVED) {
            // cell we've taken doesn't contain any ready-to-be-consumed element
            return null
        }

        // signalize the push operation that we have taken the element
        return if (cell.compareAndSet(current, CELL_STATE_EMPTY)) {
        // TODO: On success, return the element.
            current as E
        } else {
            null
        }
        // TODO: Otherwise, if the cell is empty, return `null`.

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