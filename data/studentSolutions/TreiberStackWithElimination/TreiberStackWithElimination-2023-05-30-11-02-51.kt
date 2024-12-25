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
        val arrIdx = randomCellIndex()
        if (eliminationArray[arrIdx].compareAndSet(CELL_STATE_EMPTY, element)) {
            for (popAttemptIdx in 1..ELIMINATION_WAIT_CYCLES) {
                if (eliminationArray[arrIdx].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY))
                    return true
            }

            return if (eliminationArray[arrIdx].compareAndSet(element, CELL_STATE_EMPTY))
                false
            else if (eliminationArray[arrIdx].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY))
                true
            else throw Exception()
        }

        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        for (tryI in 1..1) {
            val i = randomCellIndex()
            when (val elem = eliminationArray[i].value) {
                CELL_STATE_EMPTY -> continue
                CELL_STATE_RETRIEVED -> continue
                else -> {
                    if (eliminationArray[i].compareAndSet(elem, CELL_STATE_RETRIEVED))
                        return elem as? E
                }
            }
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