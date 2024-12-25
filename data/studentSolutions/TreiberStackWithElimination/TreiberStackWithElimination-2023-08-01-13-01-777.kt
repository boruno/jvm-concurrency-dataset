//package day1

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReferenceArray

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = AtomicReferenceArray(Array(ELIMINATION_ARRAY_SIZE) { CELL_STATE_EMPTY })

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        while (true) {
            val cellIndex = randomCellIndex()
            if (!eliminationArray.compareAndSet(cellIndex, CELL_STATE_EMPTY, element)) {
                continue // non-empty cell => try again
            }
            repeat(ELIMINATION_WAIT_CYCLES) {
                if (eliminationArray.compareAndSet(cellIndex, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                    return true
                }
            }
            return false
        }
    }

    override fun pop(): E? {
        val cellIndex = randomCellIndex()
        val value = eliminationArray.getAndSet(cellIndex, CELL_STATE_RETRIEVED)
        if (value != CELL_STATE_EMPTY && value != CELL_STATE_RETRIEVED) {
            @Suppress("UNCHECKED_CAST")
            return value as E?
        } else {
            return stack.pop()
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.length())

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = Any()

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}