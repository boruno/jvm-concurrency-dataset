package day1

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

    private val DONE = "DONE"

    private fun tryPushElimination(element: E): Boolean {
        val number = randomCellIndex()

        if (eliminationArray[number].compareAndSet(null, element)) {

            var i = 0
            while (i++ < ELIMINATION_WAIT_CYCLES) {
                if (eliminationArray[number].compareAndSet(DONE, null)) {
                    return true
                }
            }

            eliminationArray[number].compareAndSet(element, null)
            return false
        }

        return false

        // TODO("Implement me!")
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
        val index = randomCellIndex()

        val item = eliminationArray[index]
        val element = item.value
        if (element != null) return element as? E

//        if (element != DONE)
//        if (item.compareAndSet(element, DONE)) {
//            return element as E?
//        }

        return null
//        if (element != null) {
//            eliminationArray[index].value = DONE
//            return element as E?
//        }

//        TODO("Implement me!")
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