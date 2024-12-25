//package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val cellIndex = randomCellIndex()
        val cell = eliminationArray[cellIndex]

        if (cell.compareAndSet(CELL_STATE_EMPTY, element)) {
            for (i in 0 until ELIMINATION_WAIT_CYCLES) {
                if (cell.value == CELL_STATE_RETRIEVED) {
                    cell.value = CELL_STATE_EMPTY
                    return true
                }
            }
            return if (cell.compareAndSet(element, CELL_STATE_EMPTY)) {
                false
            } else {
                cell.value = CELL_STATE_EMPTY
                true
            }
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val cellIndex = randomCellIndex()
        val cell = eliminationArray[cellIndex]

        val element = cell.value
        if (element != CELL_STATE_EMPTY && cell.compareAndSet(element, CELL_STATE_RETRIEVED)) {
            return element as E?
        }
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2
        private const val ELIMINATION_WAIT_CYCLES = 1

        private val CELL_STATE_EMPTY = null
        private val CELL_STATE_RETRIEVED = Any()
    }
}
