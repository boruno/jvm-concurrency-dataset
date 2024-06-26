package day1

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
        var i = 0;
        while (i < ELIMINATION_WAIT_CYCLES) {
            val idx = randomCellIndex()
            if (eliminationArray.compareAndSet(idx, CELL_STATE_EMPTY, element)) {
                Thread.sleep(2)
                //if (eliminationArray.get(idx) == CELL_STATE_RETRIEVED) {
                if (eliminationArray.compareAndSet(idx, CELL_STATE_RETRIEVED, CELL_STATE_EMPTY)) {
                    //eliminationArray.set(idx, CELL_STATE_EMPTY)
                    return true
                }
            }
            i++
        }
        return false
    }

    override fun pop(): E? = tryPopElimination() ?: stack.pop()

    private fun tryPopElimination(): E? {
        var i = 0;
        while (i < ELIMINATION_WAIT_CYCLES) {
            val idx = randomCellIndex()
            val pushed = eliminationArray.get(idx)
            if (pushed != CELL_STATE_EMPTY && pushed != CELL_STATE_RETRIEVED) {
                if (eliminationArray.compareAndSet(idx, pushed, CELL_STATE_RETRIEVED)) {
                    return pushed as E
                }
            }
            i++
        }
        return null
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

        val rand = Random(System.currentTimeMillis())
    }
}