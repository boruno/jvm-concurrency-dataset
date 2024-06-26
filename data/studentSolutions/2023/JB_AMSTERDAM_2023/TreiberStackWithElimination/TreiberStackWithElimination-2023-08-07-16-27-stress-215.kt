package day1

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReferenceArray

open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    /**
     * Try to optimize concurrent push and pop operations, synchronizing them in an `eliminationArray` cell.
     */
    private val eliminationArray = AtomicReferenceArray<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    /**
     * Choose a random cell in `eliminationArray` and try to install the element there.
     * Wait `ELIMINATION_WAIT_CYCLES` loop cycles in hope that a concurrent `pop()` grabs the element.
     * If so, clean the cell and finish, returning `true`.
     * Otherwise, move the cell to the empty state and return `false`.
     */
    protected open fun tryPushElimination(element: E): Boolean {
        val index = randomCellIndex()
        if (!eliminationArray.compareAndSet(index, CELL_STATE_EMPTY, element)) {
            return false
        }

        repeat(ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray.compareAndSet(index, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                return true
            }
        }

        val isSuccess = eliminationArray.compareAndSet(index, element, CELL_STATE_EMPTY)
        if (isSuccess) {
            eliminationArray[index] = CELL_STATE_EMPTY
        }

        return !isSuccess
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    /**
     * Choose a random cell in `eliminationArray` and try to retrieve an element from there.
     * On success, return the element.
     * Otherwise, if the cell is empty, return `null`.
     */
    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        when (val preValue = eliminationArray[index]) {
            CELL_STATE_EMPTY -> return null
            CELL_STATE_RETRIEVED -> return null
            else -> {
                val isSuccess = eliminationArray.compareAndSet(index, preValue, CELL_STATE_RETRIEVED)
                if (isSuccess) {
                    @Suppress("UNCHECKED_CAST")
                    return preValue as E?
                } else {
                    return null
                }
            }
        }
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