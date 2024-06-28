package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

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
        val cell = eliminationArray[index]

        if (cell.compareAndSet(null, AtomicReference(element))) {
            for (i in 0 until ELIMINATION_WAIT_CYCLES) {
                val retrievedElement = cell.value
                if (retrievedElement != null) {
                    // Another thread has retrieved the element from the cell
                    cell.compareAndSet(retrievedElement, null)
                    return true
                }
                // Exponential backoff: wait before trying again
                Thread.yield()
            }

            // Elimination failed, reset the cell to empty state
            cell.compareAndSet(AtomicReference(element), null)
        }

        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val cell = eliminationArray[index]
        val elementRef = cell.value

        if (elementRef != null && cell.compareAndSet(elementRef, null)) {
            // Successfully retrieved an element from the cell
            return elementRef as E?
        }

        return null
    }

    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)

    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1 // Do not change!
    }
}
