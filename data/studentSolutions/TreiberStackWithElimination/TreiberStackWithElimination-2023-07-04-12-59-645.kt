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
        val index = randomCellIndex()
        val ref = eliminationArray[index]
        if (ref.compareAndSet(CELL_STATE_EMPTY, element)) {
            var count = 10
            while (count > 0) {
                if (ref.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                    return true
                }
                count--
            }
        }
//        while (count > 0) {
//            if (ref.value === CELL_STATE_EMPTY) {
//                if (ref.compareAndSet(CELL_STATE_EMPTY, element)) {
//                    return true
//                }
//            }
//            count--
//        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val ref = eliminationArray[index]
        val currentElement = ref.value
        if (currentElement !== CELL_STATE_EMPTY && currentElement !== CELL_STATE_RETRIEVED) {
            if (ref.compareAndSet(currentElement, CELL_STATE_RETRIEVED)) {
                return currentElement as E
            }
        }
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