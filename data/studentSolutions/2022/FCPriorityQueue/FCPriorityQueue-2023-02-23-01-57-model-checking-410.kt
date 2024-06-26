import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

private enum class FCArrayCellStates {
    POLL_OP, PEEK_OP, ADD_OP, READY
}

private data class FCArrayCell<T>(val op: FCArrayCellStates, val payload: T?)

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock: AtomicBoolean = atomic(false)
    private val operationsArray = atomicArrayOfNulls<FCArrayCell<E?>?>(Runtime.getRuntime().availableProcessors() * 2)

    private fun tryLock(): Boolean = lock.compareAndSet(expect=false, update=true)

    private fun unlock() {
        lock.value = false
    }

    private fun combinerRoutine() {
        for (i in 0 until operationsArray.size) {
            val cell = operationsArray[i].value ?: continue
            when (cell.op) {
                FCArrayCellStates.ADD_OP -> {
                    q.add(cell.payload!!)
                    operationsArray[i].value = FCArrayCell(FCArrayCellStates.READY, null)
                }
                FCArrayCellStates.PEEK_OP -> {
                    operationsArray[i].value = FCArrayCell(FCArrayCellStates.READY, q.peek())
                }
                FCArrayCellStates.POLL_OP -> {
                    operationsArray[i].value = FCArrayCell(FCArrayCellStates.READY, q.poll())
                }
                else -> continue
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            val i = (0 until operationsArray.size).random()
            if (!operationsArray[i].compareAndSet(null, FCArrayCell(FCArrayCellStates.POLL_OP, null))) {
                continue
            }
            // means we were able to publish operation, now let's try to get its result or become the combiner
            while (operationsArray[i].value!!.op != FCArrayCellStates.READY) {
                if (tryLock()) {
                    combinerRoutine()
                    unlock()
                } else {
                    continue
                }
            }
            // no one could use this cell and set it to null, since it definitely contains operation result
            return operationsArray[i].getAndSet(null)!!.payload
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            val i = (0 until operationsArray.size).random()
            if (!operationsArray[i].compareAndSet(null, FCArrayCell(FCArrayCellStates.PEEK_OP, null))) {
                continue
            }
            // means we were able to publish operation, now let's try to get its result or become the combiner
            while (operationsArray[i].value!!.op != FCArrayCellStates.READY) {
                if (tryLock()) {
                    combinerRoutine()
                    unlock()
                }
            }
            // no one could use this cell and set it to null, since it definitely contains operation result
            return operationsArray[i].getAndSet(null)!!.payload
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            val i = (0 until operationsArray.size).random()
            if (!operationsArray[i].compareAndSet(null, FCArrayCell(FCArrayCellStates.ADD_OP, element))) {
                continue
            }
            // means we were able to publish operation, now let's try to get its result or become the combiner
            while (operationsArray[i].value!!.op != FCArrayCellStates.READY) {
                if (tryLock()) {
                    combinerRoutine()
                    unlock()
                }
            }
            // no one could use this cell and set it to null, since it definitely contains operation result
            operationsArray[i].getAndSet(null)
        }
    }
}