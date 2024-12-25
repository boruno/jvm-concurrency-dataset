//package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*
import kotlin.random.Random

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
        val randomIndex = randomCellIndex()
//        val eliminationElement = eliminationArray[randomIndex]
        // TODO: and try to install the element there.
        if (!eliminationArray[randomIndex].compareAndSet(CELL_STATE_EMPTY, element)) {
            return false
        }
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.
        for (i in 1..ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray[randomIndex].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                println("Sucessfully pushed value: $element, at index $randomIndex")
                return true
            }
        }
        return if (eliminationArray[randomIndex].compareAndSet(element, CELL_STATE_EMPTY)) {
            false
        } else {
            eliminationArray[randomIndex].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)
            println("Sucessfully pushed value: $element, at index $randomIndex FFFFFFFFFFFF")
            true
        }

    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO: Choose a random cell in `eliminationArray`
        val randomIndex = randomCellIndex()
        val value = eliminationArray[randomIndex].value

        if ((value == CELL_STATE_RETRIEVED) || (value == CELL_STATE_EMPTY)) {
            return null
        }
        // TODO: and try to retrieve an element from there.
        if (eliminationArray[randomIndex].compareAndSet(value, CELL_STATE_RETRIEVED)) {
            println("Sucessfully popped value: $value, at index $randomIndex")
            return value as E?
        } else {
            return null
        }
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)

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