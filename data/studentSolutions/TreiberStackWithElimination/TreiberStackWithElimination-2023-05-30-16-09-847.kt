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

    // TODO("Implement me!")
    // TODO: Choose a random cell in `eliminationArray`
    // TODO: and try to install the element there.
    // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
    // TODO: in hope that a concurrent `pop()` grabs the
    // TODO: element. If so, clean the cell and finish,
    // TODO: returning `true`. Otherwise, move the cell
    // TODO: to the empty state and return `false`.
    private fun tryPushElimination(element: E): Boolean {
        val idx = randomCellIndex()
        if (eliminationArray[idx].compareAndSet(CELL_STATE_EMPTY, element)) {
            for (i in 0 until ELIMINATION_WAIT_CYCLES) {
                if(eliminationArray[idx].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)){
                    return true
                }
            }
            return if(eliminationArray[idx].compareAndSet(element, CELL_STATE_EMPTY)) {
                false
            } else {
                eliminationArray[idx].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)
            }
        }
        else {
            return false
        }
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    // TODO("Implement me!")
    // TODO: Choose a random cell in `eliminationArray`
    // TODO: and try to retrieve an element from there.
    // TODO: On success, return the element.
    // TODO: Otherwise, if the cell is empty, return `null`.
    @Suppress("UNCHECKED_CAST")
    private fun tryPopElimination(): E? {
       val idx = randomCellIndex()
       val el = eliminationArray[idx].value as? E
       if (el != null) eliminationArray[idx].compareAndSet(el, CELL_STATE_RETRIEVED)
       return el
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