//package day1

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

        // Try to install the element in the chosen cell.
        if (eliminationArray[index].compareAndSet(CELL_STATE_EMPTY, element)) {
            for (i in 0 until ELIMINATION_WAIT_CYCLES) {
                val value = eliminationArray[index].value

                if (value != null && eliminationArray[index].compareAndSet(value, CELL_STATE_EMPTY)) {
                    return true
                }

                // Optional: You can add a `yield()` or `parkNanos()` here to reduce busy-waiting.
                // For simplicity, we are using a loop without waiting here.
            }
            eliminationArray[index].compareAndSet(element, CELL_STATE_EMPTY)
        }

        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val randomCell = randomCellIndex()
        val value = eliminationArray[randomCell].value

        // Check if the cell contains an element and attempt to retrieve it.
        if (value != null && eliminationArray[randomCell].compareAndSet(value, CELL_STATE_EMPTY)) {
            return value as E
        }

        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        private val CELL_STATE_EMPTY = null
    }
}
