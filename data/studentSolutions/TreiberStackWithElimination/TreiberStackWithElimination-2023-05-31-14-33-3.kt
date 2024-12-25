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
        var counter = 0
        var randNum : Int = 0
        // var flag = true

        while (counter < ELIMINATION_WAIT_CYCLES) {
            randNum = randomCellIndex()
            if (eliminationArray[randNum].compareAndSet(CELL_STATE_EMPTY, element)) {
                //flag = false
                break
            }
            counter++
        }
//        if (flag) {
//            return false
//        }
        counter = 0
        while (counter ++ < ELIMINATION_WAIT_CYCLES){
            if (eliminationArray[randNum].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY))
                return true
        }

        if (eliminationArray[randNum].compareAndSet(CELL_STATE_RETRIEVED, CELL_STATE_EMPTY))
            return true
        else {
            var cellState = element
            if (eliminationArray[randNum].compareAndSet(element, CELL_STATE_EMPTY))
                return false
            else
                return true
        }

        //TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to install the element there.
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.

    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    @Suppress("UNCHECKED_CAST")
    private fun tryPopElimination(): E? {
        var counter = 0
        val tmp : E?
        var  randNum : Int

        while (counter < ELIMINATION_WAIT_CYCLES){
            randNum = randomCellIndex()
            if (eliminationArray[randNum].value != CELL_STATE_EMPTY &&
                eliminationArray[randNum].value != CELL_STATE_RETRIEVED) {
                tmp = eliminationArray[randNum].value as E?
                eliminationArray[randNum].compareAndSet(tmp, CELL_STATE_RETRIEVED)
                return tmp
            }
            counter ++
        }
        return CELL_STATE_EMPTY
        //TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.
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