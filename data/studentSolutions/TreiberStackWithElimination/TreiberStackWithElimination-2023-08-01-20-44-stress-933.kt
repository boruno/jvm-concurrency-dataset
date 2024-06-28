package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to install the element there.
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.
        var i = ELIMINATION_WAIT_CYCLES
        while (i >= 0) {
            assert(false)
            val randomCellIndex = randomCellIndex()
            val triedCell = eliminationArray[randomCellIndex]
            if (!triedCell.compareAndSet(CELL_STATE_EMPTY, element)) {
                i--
                continue
            }
            while (i >= 0) {
                i--
                if (triedCell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true
            }
            if (triedCell.compareAndSet(element, CELL_STATE_EMPTY)) return false // if it's still untouched
            if (triedCell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true // if some thread retrieved the value at the last-minute
            break
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.
        repeat(ELIMINATION_WAIT_CYCLES) {
            val randomCellIndex = randomCellIndex()
            val triedCell = eliminationArray[randomCellIndex]
            val triedValue = triedCell.value
            if (triedValue == CELL_STATE_EMPTY || triedValue == CELL_STATE_RETRIEVED) return@repeat
            @Suppress("UNCHECKED_CAST")
            return if (triedCell.compareAndSet(triedValue, CELL_STATE_RETRIEVED)) triedValue as E? else null
        }
        return null
    }

    protected fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        internal const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        internal val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        internal val CELL_STATE_RETRIEVED = Any()
    }
}

class TreiberStackWithEliminationWithoutRetryingCells<E> : Stack<E> {

    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val randomCellIndex = randomCellIndex()
        val ref = eliminationArray[randomCellIndex]
        if (!ref.compareAndSet(CELL_STATE_EMPTY, element)) return false
        repeat(ELIMINATION_WAIT_CYCLES) {
            if (ref.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true
        }
        if (ref.compareAndSet(element, CELL_STATE_EMPTY)) return false
        if (ref.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val randomCellIndex = randomCellIndex()
        val ref = eliminationArray[randomCellIndex]
        val value = ref.value
        return when {
            value == CELL_STATE_EMPTY || value == CELL_STATE_RETRIEVED -> null
            ref.compareAndSet(value, CELL_STATE_RETRIEVED) -> value as E?
            else -> null
        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        internal const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        // Initially, all cells are in EMPTY state.
        internal val CELL_STATE_EMPTY = null

        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        internal val CELL_STATE_RETRIEVED = Any()
    }
}