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
        var checkCounter = 0
        while (checkCounter <= eliminationArray.size*100) {
            val idx = randomCellIndex()
            val cell = eliminationArray[idx]
            val current = cell.value
            when (current) {
                is Waiting<*> -> { /* Cell is currently in use by another push operation. Retry with another cell. */ }
                CELL_STATE_RETRIEVED -> { /* Cell is currently in use by another push operation that has already exchanged its data. Retry with another cell. */ }
                else -> {
                    // If the cell is empty, try to set it to the Waiting state with our element.
                    if (cell.compareAndSet(CELL_STATE_EMPTY, Waiting(element))) {
                        // Now wait for a pop operation to consume our data.
                        var cyclesCounter = 0
                        while (cyclesCounter < ELIMINATION_WAIT_CYCLES) {
                            if (cell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                                return true
                            }
                            cyclesCounter++
                        }
                        // If our data was not consumed, reset the cell to the empty state and retry.
                        cell.compareAndSet(Waiting(element), CELL_STATE_EMPTY)
                    }
                }
            }
            checkCounter++
        }
        return false
    }
    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val idx = randomCellIndex()
        val cell = eliminationArray[idx]
        val current = cell.value
        when (current) {
            is Waiting<*> -> {
                // A push operation is waiting for a pop operation. Try to retrieve the data.
                if (cell.compareAndSet(current, CELL_STATE_RETRIEVED)) {
                    return current.element as E
                }
            }
            CELL_STATE_RETRIEVED -> { /* Cell is currently in use by another push operation that has already exchanged its data. Retry with another cell. */ }
            else -> { /* Cell is empty. Retry with another cell. */ }
        }
        return null
    }

    private fun randomCellIndex(): Int =
        Random(1001).nextInt(0, eliminationArray.size)

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