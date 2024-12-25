//package day1

import kotlinx.atomicfu.*
import java.util.Random
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    init {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            eliminationArray[i].value = CELL_STATE_EMPTY
        }
    }

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val idx = randomCellIndex()
        val cell = eliminationArray[idx]

        // Set cell to Waiting state if it was in Empty state
        if (!cell.compareAndSet(CELL_STATE_EMPTY, Waiting(element))) return false

        var cyclesCounter = 0
        while (cyclesCounter <= ELIMINATION_WAIT_CYCLES) {
            if (cell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                return true
            }
            cyclesCounter++
        }

        // Remove the element from the cell if not retrieved
        return !cell.compareAndSet(Waiting(element), CELL_STATE_EMPTY)
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val idx = randomCellIndex()
        val cell = eliminationArray[idx]

        val curValue = cell.value
        if (curValue is Waiting<*>) {
            if (cell.compareAndSet(curValue, CELL_STATE_RETRIEVED)) {
                return curValue.element as E
            }
        }
        return null
    }

    private fun randomCellIndex(): Int =
        Random().nextInt(0, eliminationArray.size)

    data class Waiting<E>(
        val element: E,
    )

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2
        private const val ELIMINATION_WAIT_CYCLES = 1
        private val CELL_STATE_EMPTY = null
        private val CELL_STATE_RETRIEVED = Any()
    }
}