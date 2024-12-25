//package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

/*
= The execution failed with an unexpected exception =
Execution scenario (init part):
[push(2), push(6)]
Execution scenario (parallel part):
| pop()   | push(-6) | push(-8) |
| pop()   | push(1)  | pop()    |
| push(4) | push(-8) |          |
Execution scenario (post part):
[pop(), pop()]
 */
class TreiberStackWithElimination<E: Any> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val cellIndex = randomCellIndex()
        val eliminationCell = eliminationArray[cellIndex]
        if (!eliminationCell.compareAndSet(CELL_STATE_EMPTY, element)) {
            return false
        }
        repeat(ELIMINATION_WAIT_CYCLES) {
            if (eliminationCell.compareAndSet(CELL_STATE_RETRIEVED, null)) {
                return true
            }
        }
        // Failed?
        if (eliminationCell.compareAndSet(element, null)) {
            return false
        }
        // Em?!
        assert(eliminationCell.compareAndSet(CELL_STATE_RETRIEVED, null))
        return true
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val cellIndex = randomCellIndex()
        val eliminationCell = eliminationArray[cellIndex]
        val cellValue = eliminationCell.value
        if (cellValue == CELL_STATE_EMPTY || cellValue == CELL_STATE_RETRIEVED) {
            return null
        }
        // Good element
        @Suppress("UNCHECKED_CAST")
        val element: E = cellValue as E
        if (eliminationCell.compareAndSet(element, CELL_STATE_RETRIEVED))
            return element
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