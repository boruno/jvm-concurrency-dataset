//package day1
//
//import kotlinx.atomicfu.atomicArrayOfNulls
//import java.util.concurrent.ThreadLocalRandom
//import kotlin.random.Random
//
//class TreiberStackWithElimination<E> : Stack<E> {
//    private val stack = TreiberStack<E>()
//
//    // TODO: Try to optimize concurrent push and pop operations,
//    // TODO: synchronizing them in an `eliminationArray` cell.
//    private val eliminationArray = atomicArrayOfNulls<E?>(ELIMINATION_ARRAY_SIZE)
//
//    override fun push(element: E) {
//        if (tryPushElimination(element)) return
//        stack.push(element)
//    }
//
//    private fun tryPushElimination(element: E): Boolean {
//        val randomCellIndex = randomCellIndex()
//        val waitCycles = ELIMINATION_WAIT_CYCLES
//
//        repeat(waitCycles) {
//            if (eliminationArray[randomCellIndex].compareAndSet(null, element)) {
//                Thread.sleep(Random.nextLong(10))
//                if (eliminationArray[randomCellIndex].compareAndSet(element, null)) {
//                    return true
//                } else {
//                    eliminationArray[randomCellIndex].compareAndSet(element, null)
//                }
//            } else {
//                Thread.sleep(Random.nextLong(10))
//            }
//        }
//        return false
//    }
//
//    override fun pop(): E? = tryPopElimination() ?: stack.pop()
//
//    private fun tryPopElimination(): E? {
//        val randomCellIndex = randomCellIndex()
//        val waitCycles = ELIMINATION_WAIT_CYCLES
//
//        repeat(waitCycles) {
//            val element = eliminationArray[randomCellIndex].value
//            if (element != null && eliminationArray[randomCellIndex].compareAndSet(element, null)) {
//                return element
//            } else {
//                Thread.sleep(Random.nextLong(10))
//            }
//        }
//        return null
//    }
//
//    private fun randomCellIndex(): Int =
//        ThreadLocalRandom.current().nextInt(eliminationArray.size)
//
//    companion object {
//        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
//        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!
//
//        // Initially, all cells are in EMPTY state.
//        private val CELL_STATE_EMPTY = null
//
//        // `tryPopElimination()` moves the cell state
//        // to `RETRIEVED` if the cell contains an element.
//        private val CELL_STATE_RETRIEVED = Any()
//    }
//}

//package day1

import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.concurrent.ThreadLocalRandom

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
        val randomCellIndex = randomCellIndex()
        val waitCycles = ELIMINATION_WAIT_CYCLES

        for (i in 0 until waitCycles) {
            val cellValue = eliminationArray[randomCellIndex].value
            if (cellValue == CELL_STATE_EMPTY) {
                if (eliminationArray[randomCellIndex].compareAndSet(cellValue, element)) {
                    for (j in 0 until waitCycles) {
                        Thread.sleep(10)
                        val cellValueAfterWaiting = eliminationArray[randomCellIndex].value
                        if (cellValueAfterWaiting == CELL_STATE_RETRIEVED) {
                            eliminationArray[randomCellIndex].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)
                            return true
                        }
                    }
                    eliminationArray[randomCellIndex].compareAndSet(element, CELL_STATE_EMPTY)
                    return false
                }
            }
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val randomCellIndex = randomCellIndex()
        val waitCycles = ELIMINATION_WAIT_CYCLES

        for (i in 0 until waitCycles) {
            val cellValue = eliminationArray[randomCellIndex].value
            if (cellValue != CELL_STATE_EMPTY) {
                if (eliminationArray[randomCellIndex].compareAndSet(cellValue, CELL_STATE_RETRIEVED)) {
                    eliminationArray[randomCellIndex].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)
                    return cellValue as E
                }
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
        // to `RETRIEVED` if the cell contains an element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}
