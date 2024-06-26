package day1

import java.util.concurrent.*
import java.util.concurrent.atomic.*

open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = AtomicReferenceArray<ElementWrapper<E>>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {

        // TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to install the element there.
        // TODO: Wait `ELIMINATION_WAIT_CYCLES` loop cycles
        // TODO: in hope that a concurrent `pop()` grabs the
        // TODO: element. If so, clean the cell and finish,
        // TODO: returning `true`. Otherwise, move the cell
        // TODO: to the empty state and return `false`.

        var index = randomCellIndex()
        var injected = false
        val elementToInject = ElementWrapper(element, false)

        for (i in 0..ELIMINATION_WAIT_CYCLES) {
            if (!injected && eliminationArray.compareAndSet(index, null, elementToInject))
                injected = true
            else
                index = randomCellIndex()
        }

        if (!injected)
            return false

        val result = eliminationArray.get(index)
        if (result.element === element) {
            eliminationArray.compareAndSet(index, result, null)
                    return result.taken
        }

        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO("Implement me!")
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.

        var index = randomCellIndex()

        for (i in 0..ELIMINATION_WAIT_CYCLES) {
            val retrievedElement = eliminationArray.get(index)
            if (retrievedElement != null && !retrievedElement.taken) {
                val newElement = ElementWrapper(retrievedElement.element, true)
                return if (eliminationArray.compareAndSet(index, retrievedElement, newElement)) retrievedElement.element else null
            } else {
                index = randomCellIndex()
            }
        }

        return null
    }

    private data class ElementWrapper<E>(val element: E, val taken: Boolean)

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