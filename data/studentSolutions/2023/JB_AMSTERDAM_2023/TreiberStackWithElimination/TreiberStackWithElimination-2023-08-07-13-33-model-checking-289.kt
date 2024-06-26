package day1

import java.util.Random
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
        // TODO: Choose a random cell in `eliminationArray`
        val index = randomCellIndex()
        // TODO: and try to install the element there.
        if (!eliminationArray.compareAndSet(index, CELL_STATE_EMPTY, element)) return false
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        for (i in 1..ELIMINATION_WAIT_CYCLES) {
            // TODO: in hope that a concurrent `pop()` grabs the element.
            // TODO: If so, clean the cell and finish, returning `true`.
            if (eliminationArray.compareAndSet(index, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY))
                return true
        }
        // TODO: Otherwise, move the cell to the empty state and return `false`.
        return if (eliminationArray.compareAndSet(index, element, CELL_STATE_EMPTY)) {
            false
        } else {
            assert(eliminationArray.get(index) == CELL_STATE_RETRIEVED)
            eliminationArray.set(index, CELL_STATE_EMPTY)
            true
        }
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        //TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        val index = randomCellIndex()
        // TODO: and try to retrieve an element from there.
        val value = eliminationArray.get(index)
        if (value == CELL_STATE_EMPTY) // TODO: Otherwise, if the cell is empty, return `null`.
            return null
        // TODO: On success, return the element.
        if (eliminationArray.compareAndSet(index, value, CELL_STATE_RETRIEVED))
            return value as E
        else
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