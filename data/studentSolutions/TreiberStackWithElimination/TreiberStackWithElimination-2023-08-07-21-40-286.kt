package day1

import java.util.concurrent.*
import java.util.concurrent.atomic.*

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
        val index = randomCellIndex()
        println("pushing $element at $index")
        if (eliminationArray.compareAndSet(index, CELL_STATE_EMPTY, element)) {
            println("Starting wait cycle $element")
            for (i in 0..ELIMINATION_WAIT_CYCLES) {
                if (eliminationArray.compareAndSet(index, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                    println("Element at $index was poped")
                    return true
                }
            }
            println("Element $element failed to pop - emptying")
            if (eliminationArray.compareAndSet(index, element, CELL_STATE_EMPTY)) {
                println("Empty for $element was successful")
            }
        }

        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.
        val index = randomCellIndex()
        val element = eliminationArray.get(index)
        return if (element != CELL_STATE_EMPTY && element != CELL_STATE_RETRIEVED) {
            eliminationArray.compareAndSet(index, element, CELL_STATE_RETRIEVED)
            @Suppress("UNCHECKED_CAST")
            element as? E
        } else {
            null
        }
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