//package day1

import kotlinx.atomicfu.*
import java.lang.IllegalStateException
import java.util.concurrent.*

class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    private sealed interface EliminationUnit<out T>

    @Suppress("PrivatePropertyName")
    private val EmptyUnit = null
    private inner class OccupiedUnit(val element: E): EliminationUnit<E>
    private object ReleasedUnit: EliminationUnit<Nothing>

    private val eliminationArray = atomicArrayOfNulls<EliminationUnit<E>?>(ELIMINATION_ARRAY_SIZE)
//    private val eliminationArray = atomicArrayOfNulls<Any?>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    private fun tryPushElimination(element: E): Boolean {
        val eliminationCellIndex = randomCellIndex()
        val eliminationCell = eliminationArray[eliminationCellIndex]
        val occupiedUnit = OccupiedUnit(element)
        if (eliminationCell.compareAndSet(EmptyUnit, occupiedUnit)) {
            for (i in 0 until ELIMINATION_WAIT_CYCLES) {
                if (eliminationCell.compareAndSet(ReleasedUnit, EmptyUnit)) return true
            }
            if (eliminationCell.compareAndSet(occupiedUnit, EmptyUnit)) return false
            // this CAS is most probably excessive, but Oh Well, it doesn't hurt either
            if (eliminationCell.compareAndSet(ReleasedUnit, EmptyUnit)) return true
            throw IllegalStateException("unreachable tryPushElimination execution state")
        } else {
            return false
        }
//        val eliminationCellIndex = randomCellIndex()
//        val eliminationCell = eliminationArray[eliminationCellIndex]
//        if (eliminationCell.compareAndSet(CELL_STATE_EMPTY, element)) {
//            for (i in 0 until ELIMINATION_WAIT_CYCLES) {
//                if (eliminationCell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true
//            }
//            if (eliminationCell.compareAndSet(element, CELL_STATE_EMPTY)) return false
//            if (eliminationCell.compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) return true
//            throw IllegalStateException("unreachable tryPushElimination execution state")
//        } else {
//            return false
//        }
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val eliminationCellIndex = randomCellIndex()
        val eliminationCell = eliminationArray[eliminationCellIndex]
        val occupiedUnit = when (val eliminationUnit = eliminationCell.value) {
            EmptyUnit, ReleasedUnit -> return null
            is OccupiedUnit -> eliminationUnit
        }
        return if (eliminationCell.compareAndSet(occupiedUnit, ReleasedUnit)) {
            occupiedUnit.element
        } else {
            null
        }
//        val eliminationCellIndex = randomCellIndex()
//        val eliminationCell = eliminationArray[eliminationCellIndex]
//        val element = eliminationCell.value
//        if (element == CELL_STATE_EMPTY || element == CELL_STATE_RETRIEVED) return CELL_STATE_EMPTY
//        return if (eliminationCell.compareAndSet(element, CELL_STATE_RETRIEVED)) {
//            element as E
//        } else {
//            CELL_STATE_EMPTY
//        }
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(0, eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!

//        // Initially, all cells are in EMPTY state.
//        private val CELL_STATE_EMPTY = null
//
//        // `tryPopElimination()` moves the cell state
//        // to `RETRIEVED` if the cell contains element.
//        private val CELL_STATE_RETRIEVED = Any()
    }
}