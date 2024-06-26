package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val index = randomCellIndex()
        val e = eliminationArray[index]
        if (!e.compareAndSet(CELL_STATE_EMPTY, element)) {
            // could not install element there, cell not empty
            return false
        }

        for (i in 1..ELIMINATION_WAIT_CYCLES) {
            if (e.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                return true
            }
        }
        e.compareAndSet(element, null)
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val e = eliminationArray[index]
        val v = e.value
        return when {
            v != CELL_STATE_EMPTY && v != CELL_STATE_RETRIEVED -> {
                if (e.compareAndSet(v, CELL_STATE_RETRIEVED)) {
                    v as E
                } else {
                    null
                }
            }
            else -> null
        }
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