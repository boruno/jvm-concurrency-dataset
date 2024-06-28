package day1

import kotlinx.atomicfu.*
import java.util.concurrent.*

class TreiberStackWithElimination<E>(arrSize: Int = ELIMINATION_ARRAY_SIZE) : Stack<E> {
    private val stack = TreiberStack<E>()
    
    private val eliminationArray = atomicArrayOfNulls<Any?>(arrSize)
    
    override fun push(element: E) {
        if (tryPushElimination(element)) return
        stack.push(element)
    }
    
    private fun tryPushElimination(element: E): Boolean {
        val index = randomCellIndex()

        if (!eliminationArray[index].compareAndSet(null, element)) return false
        
        repeat (ELIMINATION_WAIT_CYCLES) {
            if (eliminationArray[index].value == CELL_STATE_RETRIEVED) {
                eliminationArray[index].value = CELL_STATE_RETRIEVED
                return true
            }
        }

        if (eliminationArray[index].compareAndSet(element, null)) {
            return false
        }

        eliminationArray[index].value = CELL_STATE_RETRIEVED
        return true
    }
    
    override fun pop(): E? = tryPopElimination() ?: stack.pop()
    
    private fun tryPopElimination(): E? {
        repeat(4) {
            val index = randomCellIndex()

            val value = eliminationArray[index].value
            if (value == null || value == CELL_STATE_RETRIEVED) return null

            @Suppress("UNCHECKED_CAST")
            val element = value as E
            if (eliminationArray[index].compareAndSet(element, CELL_STATE_RETRIEVED)) {
                return element
            }
        }
        
        return null
    }
    
    private fun randomCellIndex(): Int =
        ThreadLocalRandom.current().nextInt(eliminationArray.size)
    
    companion object {
        private const val ELIMINATION_ARRAY_SIZE = 2 // Do not change!
        private const val ELIMINATION_WAIT_CYCLES = 1000 // Do not change!
        
        // `tryPopElimination()` moves the cell state
        // to `RETRIEVED` if the cell contains element.
        private val CELL_STATE_RETRIEVED = Any()
    }
}