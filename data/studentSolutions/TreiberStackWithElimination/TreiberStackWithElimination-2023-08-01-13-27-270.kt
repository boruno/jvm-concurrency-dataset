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
        val randomCellIndex = randomCellIndex()
        if (!eliminationArray[randomCellIndex].compareAndSet(CELL_STATE_EMPTY, element)) {
            return false
        }

        for (i in 0..ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray[randomCellIndex].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                return true
            }
        }
        val prev = eliminationArray[randomCellIndex].getAndSet(CELL_STATE_EMPTY)
        return prev == CELL_STATE_RETRIEVED
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val randomCellIndex = randomCellIndex()
        val result = eliminationArray[randomCellIndex].getAndUpdate { v ->
            when (v) {
                CELL_STATE_EMPTY, CELL_STATE_RETRIEVED -> v
                else -> CELL_STATE_RETRIEVED
            }
        }
        return when (result) {
            CELL_STATE_EMPTY, CELL_STATE_RETRIEVED -> null
            else -> result as E
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 10 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 200 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}