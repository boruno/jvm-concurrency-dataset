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
        val eliminator = eliminationArray[randomCellIndex]

        if (eliminator != CELL_STATE_EMPTY) return false

        eliminationArray.set(randomCellIndex, element)

        repeat(ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray.compareAndSet(randomCellIndex, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY))
                return true
        }

        return eliminationArray.getAndSet(randomCellIndex, CELL_STATE_EMPTY) == CELL_STATE_RETRIEVED
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val randomCellIndex = randomCellIndex()
        val eliminator = eliminationArray[randomCellIndex]

        return when {
            eliminator == CELL_STATE_EMPTY || eliminator == CELL_STATE_RETRIEVED -> null
            eliminationArray.compareAndSet(randomCellIndex, eliminator, CELL_STATE_RETRIEVED) -> eliminator as E?
            else -> tryPopElimination()
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