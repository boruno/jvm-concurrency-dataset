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
        val idx = randomCellIndex()
        if (!eliminationArray.compareAndSet(idx, CELL_STATE_EMPTY, element))
            return false

        var cycles = 0
        while (cycles < ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray.compareAndSet(idx, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                return true
            }
            cycles++
        }

        while (true) {
            val current = eliminationArray.get(idx)
            if (current == CELL_STATE_RETRIEVED) {
                eliminationArray.set(idx, CELL_STATE_EMPTY)
                return true
            }

            if (eliminationArray.compareAndSet(idx, element, CELL_STATE_EMPTY)) {
                return false
            }
        }
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()


    @Suppress("UNCHECKED_CAST")
    private fun tryPopElimination(): E? {
        val idx = randomCellIndex()
        val current = eliminationArray.get(idx) as E ?: return null

        if (eliminationArray.compareAndSet(idx, current, CELL_STATE_RETRIEVED)) {
            return current
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