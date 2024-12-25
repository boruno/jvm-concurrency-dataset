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
//        TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to install the element there.
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.
        var i = 0
        var pushed = false
        val randomCellIndex = randomCellIndex()
//        while (i++ < ELIMINATION_WAIT_CYCLES) {
            val randomCell = eliminationArray[randomCellIndex]
            val currentValue = randomCell.value
//            if (currentValue != CELL_STATE_EMPTY)
//                continue
            if (randomCell.compareAndSet(CELL_STATE_EMPTY, element)) {
//                pushed = true
                var j = 0
                while (j++ < ELIMINATION_WAIT_CYCLES) {
                    if (randomCell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                        return true
                    }
//                    j++
                }
                if (randomCell.compareAndSet(element, CELL_STATE_EMPTY)) {
                    return false
                }
                if (randomCell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                    return true
                }

            }

//            i++
//        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
//        TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.
        var i = 0
        val randomCellIndex = randomCellIndex()
        val cell = eliminationArray[randomCellIndex]
        val value = cell.value
        if (cell.compareAndSet(value, CELL_STATE_RETRIEVED)) {
            return value as E?
        }
////        while (i++ < ELIMINATION_WAIT_CYCLES) {
//            val currentValue = cell.value
//            if (currentValue == CELL_STATE_EMPTY)
//                continue
//            if (cell.compareAndSet(currentValue, CELL_STATE_RETRIEVED)) {
//                var j = 0;
//                while (true) {
//                    if (cell.value == CELL_STATE_EMPTY) {
//                        break
//                    }
//                }
//                return currentValue as E?
//            }
////            i++
////        }
        return null
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