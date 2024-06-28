package day1

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReferenceArray

open class TreiberStackWithElimination<E> : Stack<E> {
    private val stack = TreiberStack<E>()

    // TODO: Try to optimize concurrent push and pop operations,
    // TODO: synchronizing them in an `eliminationArray` cell.
    private val eliminationArray = AtomicReferenceArray<EliminationState<Any?>>(ELIMINATION_ARRAY_SIZE).also {
        for (i in 0 until ELIMINATION_ARRAY_SIZE) {
            it[i] = EliminationState.Empty
        }
    }

    sealed class EliminationState<E>(open val element: E? = null) {
        class Value<E>(override val element: E) : EliminationState<E>(element)
        object Waiting : EliminationState<Any?>()
        object Empty : EliminationState<Any?>()
    }

    enum class State()

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

        val randomIndex = randomCellIndex()
        if (eliminationArray.compareAndExchange(
                randomIndex,
                EliminationState.Empty,
                EliminationState.Value(element)
            ) != EliminationState.Empty
        ) {
            return false
        }

        for (i in 1..ELIMINATION_WAIT_CYCLES) {
            continue
        }

        return eliminationArray[randomIndex] == EliminationState.Waiting
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        // TODO: Choose a random cell in `eliminationArray`
        // TODO: and try to retrieve an element from there.
        // TODO: On success, return the element.
        // TODO: Otherwise, if the cell is empty, return `null`.

        val randomIndex = randomCellIndex()
        val value = eliminationArray[randomIndex]
        val realValue = eliminationArray.compareAndExchange(randomIndex, value, EliminationState.Waiting)
        if (realValue is EliminationState.Value) {
            return realValue.element as? E
        } else {
            eliminationArray.compareAndExchange(randomIndex, EliminationState.Waiting, EliminationState.Empty)
            return null
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