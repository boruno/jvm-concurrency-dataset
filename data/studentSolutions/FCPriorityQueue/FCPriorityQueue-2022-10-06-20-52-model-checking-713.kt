import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private class Combiner<E : Comparable<E>> {
        private sealed class CellState<E : Comparable<E>> {
            // null -- empty
            class Operation<E : Comparable<E>>(
                private val block: PriorityQueue<E>.() -> E?
            ) : CellState<E>() {
                fun run(queue: PriorityQueue<E>): E? = block(queue)
            }

            data class Result<E : Comparable<E>>(val result: E?) : CellState<E>()
        }


        private val random = Random(0)
        private val array = atomicArrayOfNulls<CellState<E>>(3)
        private val lock = ReentrantLock()
        private val q = PriorityQueue<E>()


        private fun combine() {
            for (i in 0 until array.size) {
                val result = (array[i].value as? CellState.Operation<E>)?.run(q)
                array[i].value = CellState.Result(result)
            }
        }

        private fun waitFreeCellAndSetToIt(operation: CellState.Operation<E>): Int {
            while (true) {
                val index = random.nextInt(array.size)
                val cellCandidate = array[index]
                if (cellCandidate.compareAndSet(null, operation)) {
                    return index
                }
            }
        }

        fun perform(block: PriorityQueue<E>.() -> E?): E? {
            val operation = CellState.Operation(block)
            val selectedCell = waitFreeCellAndSetToIt(operation)
            val cell = array[selectedCell]

            while (true) {
                val cellValue = cell.value
                if (cellValue is CellState.Result<*>) {
                    // wow, someone helped me! (maybe it was I)
                    val res = cellValue.result as E?
                    cell.value = null
                    return res
                }
                if (lock.tryLock()) {
                    // i'm combiner
                    combine()
                    lock.unlock()
                }
            }
        }
    }

    private val combiner = Combiner<E>()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll() = combiner.perform { poll() }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek() = combiner.perform { peek() }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        combiner.perform {
            add(element)
            null
        }
    }
}