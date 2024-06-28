package day1

import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.updateAndGet
import java.util.concurrent.ThreadLocalRandom

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val index = randomCellIndex()
        val cell = eliminationArray[index]

        repeat(ELIMINATION_WAIT_CYCLES) {
            if (cell.compareAndSet(CELL_STATE_EMPTY, element)) {
                for (i in 0 until ELIMINATION_WAIT_CYCLES) {
                    if (cell.value === CELL_STATE_RETRIEVED) {
                        cell.value = CELL_STATE_EMPTY
                        return true
                    }
                }
                return cell.compareAndSet(element, CELL_STATE_EMPTY)
            }
        }

        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val cell = eliminationArray[index]

        val value = cell.value
        if (value !== CELL_STATE_EMPTY && value !== CELL_STATE_RETRIEVED) {
            if (cell.compareAndSet(value, CELL_STATE_RETRIEVED)) {
                @Suppress("UNCHECKED_CAST")
                return value as E?
            }
        }
        
        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

        private val CELL_STATE_EMPTY = null
        private val CELL_STATE_RETRIEVED = Any()
    }
}