package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*
import kotlin.reflect.jvm.internal.impl.util.ModuleVisibilityHelper.EMPTY

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
        // TODO: Choose a random cell in `eliminationArray`
        val randomCellIndex = randomCellIndex()
        val randomCell = eliminationArray[randomCellIndex]

        // TODO: and try to install the element there.
        if (randomCell.compareAndSet(CELL_STATE_EMPTY, element)) {
            // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
            // TODO: in hope that a concurrent `pop()` grabs the
            // TODO: element. If so, clean the cell and finish,
            // TODO: returning `true`. Otherwise, move the cell
            // TODO: to the empty state and return `false`.
            var counter = ELIMINATION_WAIT_CYCLES
            while (counter > 0) {
                counter--
            }

            var result: Boolean? = null
            randomCell.update {
                result = if (it == CELL_STATE_RETRIEVED) true else false
                CELL_STATE_EMPTY
            }
            return result!!
        } else {
            return false
        }
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO: Choose a random cell in `eliminationArray`
        val randomCellIndex = randomCellIndex()
        val randomCell = eliminationArray[randomCellIndex]

        var result: E? = null
        randomCell.getAndUpdate {
            if (it != CELL_STATE_EMPTY) {
                result = it as E
                CELL_STATE_RETRIEVED
            } else {
                CELL_STATE_EMPTY
            }
        }
        return result
//        // TODO: and try to retrieve an element from there.
//        if (randomCell.value != CELL_STATE_EMPTY) {
//            return randomCell.getAndSet(CELL_STATE_RETRIEVED) as? E
//        }
//
//        randomCell.value = CELL_STATE_EMPTY
//        return null
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