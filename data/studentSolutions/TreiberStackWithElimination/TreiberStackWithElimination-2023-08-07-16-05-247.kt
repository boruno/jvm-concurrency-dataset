//package day1

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
        val randomCellIndex = randomCellIndex()
        if (eliminationArray.compareAndSet(randomCellIndex, CELL_STATE_EMPTY, element)) {
            repeat(ELIMINATION_WAIT_CYCLES) {
                if (eliminationArray.compareAndSet(randomCellIndex, CELL_STATE_RETRIEVED, null)) {
                    return true
                }
            }
            eliminationArray.compareAndSet(randomCellIndex, element, null)
            return false
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val randomCellIndex = randomCellIndex()
        val randomVal = eliminationArray.get(randomCellIndex)
        if (randomVal == null || randomVal == CELL_STATE_RETRIEVED) {

        }
        val oldValue = eliminationArray.getAndUpdate(randomCellIndex) { cell ->
            if (cell != null && cell != CELL_STATE_RETRIEVED) {
                CELL_STATE_RETRIEVED
            }
            cell
        }

        return if (oldValue == CELL_STATE_RETRIEVED) null else oldValue as E?
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