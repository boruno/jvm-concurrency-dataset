//package day1

import java.util.concurrent.*
import java.util.concurrent.atomic.*
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
//        val cellIndex = Random.nextInt(eliminationArray.length())
        val cellIndex = 1

        val curCellValue = eliminationArray[cellIndex]
        if (curCellValue != CELL_STATE_EMPTY) return false

        if (!eliminationArray.compareAndSet(cellIndex, curCellValue, element)) return false

        for (i in 0 until ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray.compareAndSet(cellIndex, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                return true
            }
        }
        eliminationArray.set(cellIndex, null)
        return false


//        TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to install the element there.
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
//        val cellIndex = Random.nextInt(eliminationArray.length())
        val cellIndex = 1

        for (i in 0 until ELIMINATION_WAIT_CYCLES) {
            val curCellValue = eliminationArray[cellIndex]

            if (curCellValue == CELL_STATE_EMPTY || curCellValue == CELL_STATE_RETRIEVED) continue

            if (eliminationArray.compareAndSet(cellIndex, curCellValue, CELL_STATE_RETRIEVED)) {
                @Suppress("UNCHECKED_CAST")
                return curCellValue as E
            }
        }
        return null

//        TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.
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