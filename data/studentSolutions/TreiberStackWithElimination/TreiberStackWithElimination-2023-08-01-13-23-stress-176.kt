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
        val index = randomCellIndex()
        val cell = eliminationArray[index]
        if (cell.compareAndSet(CELL_STATE_EMPTY, element)) {
            // Wait for a short period in hope that a concurrent pop() grabs the element.
            var waitCycles = ELIMINATION_WAIT_CYCLES
            while (waitCycles > 0) {
                if (cell.compareAndSet(element, CELL_STATE_EMPTY)) {
                    // Another thread took the element, and we can finish.
                    return true
                }
                waitCycles--
            }
            // We reached here, meaning no one took the element.
            cell.compareAndSet(element, CELL_STATE_EMPTY) // Move the cell to the empty state.
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val cell = eliminationArray[index]
        val element = cell.value
        if (element != null && cell.compareAndSet(element, CELL_STATE_EMPTY)) {
            // We successfully retrieved an element from the cell.
            return element as E
        }
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        private val CELL_STATE_EMPTY = null
    }
}
