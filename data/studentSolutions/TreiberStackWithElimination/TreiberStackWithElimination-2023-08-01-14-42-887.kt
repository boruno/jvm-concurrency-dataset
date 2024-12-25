//package day1

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
//        var index: Int
//        do {
//            index = randomCellIndex()
//        } while (!eliminationArray[index].compareAndSet(CELL_STATE_EMPTY, element))
//

        val index = randomCellIndex()
        if (!eliminationArray[index].compareAndSet(CELL_STATE_EMPTY, element))
            return false

        for (i in 0 until ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray[index].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                return true // element picked
            }
        }

        if (eliminationArray[index].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
            return true // element picked
        } else {
            if (eliminationArray[index].compareAndSet(element, CELL_STATE_EMPTY))
                return false
            else
                 throw Exception()
        }
    }

    private fun cleanup(index: Int) {
        eliminationArray[index].value = CELL_STATE_EMPTY
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val cachedValue = eliminationArray[index].value

        return when (cachedValue) {
            CELL_STATE_EMPTY, CELL_STATE_RETRIEVED -> null
            else -> if (eliminationArray[index].compareAndSet(cachedValue, CELL_STATE_RETRIEVED))
                cachedValue as? E?
            else null
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