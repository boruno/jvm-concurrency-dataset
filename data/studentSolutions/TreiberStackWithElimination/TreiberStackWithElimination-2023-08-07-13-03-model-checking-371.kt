package day1

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReferenceArray

open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = AtomicReferenceArray<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {
        while (true) {
            val index = randomCellIndex()
            if (eliminationArray.compareAndSet(index, CELL_STATE_EMPTY, element)) {
                var i = 0
                while (i++ < ELIMINATION_WAIT_CYCLES) {
                    if (eliminationArray.compareAndSet(index, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                        return true
                    }
                }
                if (eliminationArray.compareAndSet(index, element, CELL_STATE_EMPTY)) {
                    return false
                }
                if (eliminationArray.compareAndSet(index, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                    return true
                }
                error("unexpected state")
            }
        }
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val element = eliminationArray.get(index)
        if (element in setOf(CELL_STATE_EMPTY, CELL_STATE_RETRIEVED)
            || !eliminationArray.compareAndSet(index, element, CELL_STATE_RETRIEVED)
        ) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return element as E
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