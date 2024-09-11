package day1

import java.util.concurrent.*
import java.util.concurrent.atomic.*

open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = AtomicReferenceArray<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {
        val index = randomCellIndex()

        if (eliminationArray.compareAndSet(index, CELL_STATE_EMPTY, element)) {
            var counter = ELIMINATION_WAIT_CYCLES
            while (counter > 0) {
                if (eliminationArray.get(index) == CELL_STATE_RETRIEVED) {
                    eliminationArray.set(index, CELL_STATE_EMPTY)
                    return true
                }
                counter--
            }
            eliminationArray.set(index, CELL_STATE_EMPTY)
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val v = eliminationArray.get(index) as E?
        if (v != CELL_STATE_RETRIEVED
            && v != CELL_STATE_EMPTY
            && eliminationArray.compareAndSet(index, v, CELL_STATE_RETRIEVED)) {
            return v
        }
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.length())

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