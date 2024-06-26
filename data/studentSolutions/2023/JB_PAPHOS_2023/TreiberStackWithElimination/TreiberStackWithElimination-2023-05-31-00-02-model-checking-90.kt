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
        val idx = randomCellIndex()
        if (eliminationArray[idx].compareAndSet(CELL_STATE_EMPTY, element)) {
            repeat(ELIMINATION_WAIT_CYCLES) {
                if (eliminationArray[idx].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                    return true
                }
            }
            val ret = eliminationArray[idx].value == element
            eliminationArray[idx].value = CELL_STATE_EMPTY
            return ret
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    @Suppress("UNCHECKED_CAST")
    private fun tryPopElimination(): E? {
        val idx = randomCellIndex()
        val e = eliminationArray[idx].value
        if (e != CELL_STATE_EMPTY
            && e != CELL_STATE_RETRIEVED
            && eliminationArray[idx].compareAndSet(e, CELL_STATE_RETRIEVED)) {
            return e as E
        }
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

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