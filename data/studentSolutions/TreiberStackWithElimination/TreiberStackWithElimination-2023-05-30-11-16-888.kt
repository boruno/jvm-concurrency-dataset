//package day1

import kotlinx.atomicfu.atomicArrayOfNulls
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
        val chosenIndex = randomCellIndex()
        val cell = eliminationArray[chosenIndex]
        if (cell.compareAndSet(CELL_STATE_EMPTY, element)) {
            repeat(ELIMINATION_WAIT_CYCLES) {
                // Try to wait if some other thread will take the value
                if (cell.compareAndSet(CELL_STATE_RETRIEVED, null)) {
                    return true
                }
            }
            // If id didn't happen, reset value from the array to move it to stack
            return !cell.compareAndSet(element, null)
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val chosenIndex = randomCellIndex()
        val cell = eliminationArray[chosenIndex]
        val gotValue = cell.value

        // Check the correctness of the value
        if (gotValue == CELL_STATE_RETRIEVED) return null
        if (gotValue == CELL_STATE_EMPTY) return null

        // Verify that it's still in the array and return it
        if (cell.compareAndSet(gotValue, CELL_STATE_RETRIEVED)) {
            return gotValue as E?
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