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
        // Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // in hope that a concurrent `pop()` grabs the
        // element. If so, clean the cell and finish,
        // returning `true`. Otherwise, move the cell
        // to the empty state and return `false`.

        val idx = randomCellIndex()
        // TODO can try other cells
        if (eliminationArray[idx].compareAndSet(CELL_STATE_EMPTY, element)) {
            for (i in 1..ELIMINATION_WAIT_CYCLES) {
                if (eliminationArray[idx].compareAndSet(CELL_STATE_RETRIEVED, null)) {
                    return true
                }
            }
            eliminationArray[idx].getAndSet(CELL_STATE_EMPTY)
            return false
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // Choose a random cell in `eliminationArray`
        // and try to retrieve an element from there.
        // On success, return the element.
        // Otherwise, if the cell is empty, return `null`.

        val idx = randomCellIndex()
        // TODO can try other cells
        val element = eliminationArray[idx].getAndSet(CELL_STATE_RETRIEVED)
        if (element != CELL_STATE_EMPTY && element != CELL_STATE_RETRIEVED) {
            return element as E
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