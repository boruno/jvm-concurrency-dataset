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
        repeat(ELIMINATION_SEARCH_CYCLES) {
            val index = randomCellIndex()

            val value = eliminationArray[index].value

            if (value == CELL_STATE_EMPTY && eliminationArray[index].compareAndSet(null, element)) {
                repeat(ELIMINATION_WAIT_CYCLES) {
                    if (eliminationArray[index].compareAndSet(CELL_STATE_RETRIEVED, null)) {
                        return true
                    }
                }

                if (!eliminationArray[index].compareAndSet(element, null)) {
                    eliminationArray[index].compareAndSet(CELL_STATE_RETRIEVED, null)
                }
            }
        }

        return false

        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to install the element there.
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        repeat(ELIMINATION_SEARCH_CYCLES) {
            val index = randomCellIndex()
            val value = eliminationArray[index].value

            if (value == CELL_STATE_EMPTY || value == CELL_STATE_RETRIEVED) {
                return@repeat
            }

            if (eliminationArray[index].compareAndSet(value, CELL_STATE_RETRIEVED)) {
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
        private const val ELIMINATION_SEARCH_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}