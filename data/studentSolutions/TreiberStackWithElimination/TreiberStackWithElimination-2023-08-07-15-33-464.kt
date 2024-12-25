//package day1

import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = AtomicReferenceArray<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to install the element there.
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.
        val randomCellId = randomCellIndex()
        val current = eliminationArray.get(randomCellId)
        if (current !== CELL_STATE_EMPTY && current !== CELL_STATE_RETRIEVED) {
            return false
        }
        if (!eliminationArray.compareAndSet(randomCellId, current, element)) {
            return false
        }
        for (i in 0 until ELIMINATION_WAIT_CYCLES);
        val value = eliminationArray.getAndSet(randomCellId, CELL_STATE_EMPTY)
        return value === CELL_STATE_EMPTY
//        if (!eliminationArray.compareAndSet(randomCellId, element, CELL_STATE_EMPTY)) {
//             retrieved?
//            eliminationArray.compareAndSet(randomCellId, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)
//            return true
//        }
//        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.
        val randomCellId = randomCellIndex()
        val currentValue = eliminationArray.get(randomCellId)
        if (currentValue === CELL_STATE_RETRIEVED || currentValue == null) {
            return null
        }
        if (eliminationArray.compareAndSet(randomCellId, currentValue, CELL_STATE_RETRIEVED)) {
            return currentValue as E
        }
        return null
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