//package day1

import java.util.concurrent.*
import java.util.concurrent.atomic.*

open class TreiberStackWithElimination<E> : Stack<E> {
    private class Node<out T>(val value: T)
    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = AtomicReferenceArray<Node<E?>>(ELIMINATION_ARRAY_SIZE)

    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }

    protected open fun tryPushElimination(element: E): Boolean {
        val index = randomCellIndex()
        val addedNode = Node(element)
        if (!eliminationArray.compareAndSet(index, CELL_STATE_EMPTY, addedNode)) return false
        try {
            repeat(ELIMINATION_WAIT_CYCLES) {
                if (eliminationArray[index] === CELL_STATE_RETRIEVED) {
                    return true
                }
            }
            return false
        } finally {
            eliminationArray[index] = CELL_STATE_EMPTY
        }
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private tailrec fun tryPopElimination(): E? {
        val index = randomCellIndex()
        val eliminated = eliminationArray[index]
        @Suppress("UNCHECKED_CAST")
        return when {
            eliminated === CELL_STATE_EMPTY || eliminated === CELL_STATE_RETRIEVED -> null
            eliminationArray.compareAndSet(index, eliminated, CELL_STATE_RETRIEVED) -> eliminated.value
            else -> tryPopElimination()
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
        private val CELL_STATE_RETRIEVED = Node(null)
    }
}