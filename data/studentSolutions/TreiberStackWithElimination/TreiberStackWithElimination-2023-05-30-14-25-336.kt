//package day1

import kotlinx.atomicfu.*
import java.util.Random
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    init {
        // Initially, all cells are in EMPTY state.
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
        if (cell.value == CELL_STATE_WAITING || cell.value == CELL_STATE_BUSY) return false

        if (!cell.compareAndSet(CELL_STATE_EMPTY, CELL_STATE_WAITING)) return false

        var cyclesCounter = 0
        while (cyclesCounter <= ELIMINATION_WAIT_CYCLES) {
            if (cell.compareAndSet(CELL_STATE_BUSY, CELL_STATE_EMPTY)) {
                return true
            }
            cyclesCounter++
        }

        if (!cell.compareAndSet(CELL_STATE_WAITING, CELL_STATE_EMPTY)) {
            // A concurrent pop operation has taken the value.
            return true
        }

        return false
    }

    private fun tryPopElimination(): E? {
        val idx = randomCellIndex()
        val cell = eliminationArray[idx]
        if (cell.value == CELL_STATE_EMPTY || cell.value == CELL_STATE_BUSY) return null

        val element = cell.value
        if (cell.compareAndSet(CELL_STATE_WAITING, CELL_STATE_BUSY)) {
            // Took the value from a concurrent push operation.
            return element as E
        }
        return null
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun randomCellIndex(): Int =
//        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)
        Random(1001).nextInt(0, eliminationArray.size)

    data class Waiting<E>(
        val element: E,
    )
    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()

        private val CELL_STATE_WAITING = Any()

        private val CELL_STATE_BUSY = Any()
    }
}