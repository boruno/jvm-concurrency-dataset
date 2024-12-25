//package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*
import kotlin.random.Random

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
        for (putAttemptIdx in 1..ELIMINATION_PUT_ATTEMPT_COUNT) {
            val arrIdx = randomCellIndex()
            if (eliminationArray[arrIdx].compareAndSet(CELL_STATE_EMPTY, element)) {
                for (popAttemptIdx in 1..ELIMINATION_WAIT_CYCLES) {
                    if (eliminationArray[arrIdx].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                        return true
                    }
                }
                return !eliminationArray[arrIdx].compareAndSet(element, CELL_STATE_EMPTY)
            }
        }

        return false;
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val arrIdx = randomCellIndex()
        val elem = eliminationArray[arrIdx].value ?: return null

        if (eliminationArray[arrIdx].compareAndSet(elem, CELL_STATE_RETRIEVED))
            return elem as? E
        else return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!
        private const val ELIMINATION_PUT_ATTEMPT_COUNT = 4

        // Initially, all cells are in EMPTY state.
        private val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}