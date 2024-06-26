import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

private enum class FCArrayCellStates {
    POLL_OP, PEEK_OP, ADD_OP, READY
}

private data class FCArrayCell<T>(var op: FCArrayCellStates, var payload: T?)

class FCPriorityQueue<E : Comparable<E>> {
    private val rnd = Random()
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val operationsArray = atomicArrayOfNulls<FCArrayCell<E?>?>(Runtime.getRuntime().availableProcessors() * 4)

//    private fun tryLock(): Boolean = lock.compareAndSet(expect=false, update=true)

//    private fun unlock() {
//        lock.value = false
//    }

    private fun combinerRoutine() {
        for (i in 0 until operationsArray.size) {
            val cell = operationsArray[i].value ?: continue
            when (cell.op) {
                FCArrayCellStates.ADD_OP -> {
                    q.add(cell.payload!!)
                }
                FCArrayCellStates.PEEK_OP -> {
                    cell.payload = q.peek()
                }
                FCArrayCellStates.POLL_OP -> {
                    cell.payload = q.poll()
                }
                else -> continue
            }
            cell.op = FCArrayCellStates.READY
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            val i = rnd.nextInt(operationsArray.size)
            if (!operationsArray[i].compareAndSet(null, FCArrayCell(FCArrayCellStates.POLL_OP, null))) {
                continue
            }
            // means we were able to publish operation, now let's try to get its result or become the combiner
            while (operationsArray[i].value!!.op != FCArrayCellStates.READY) {
                if (lock.tryLock()) {
                    combinerRoutine()
                    lock.unlock()
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
            val i = rnd.nextInt(operationsArray.size)
            if (!operationsArray[i].compareAndSet(null, FCArrayCell(FCArrayCellStates.PEEK_OP, null))) {
                continue
            }
            // means we were able to publish operation, now let's try to get its result or become the combiner
            while (operationsArray[i].value!!.op != FCArrayCellStates.READY) {
                if (lock.tryLock()) {
                    combinerRoutine()
                    lock.unlock()
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
            val i = rnd.nextInt(operationsArray.size)
            if (!operationsArray[i].compareAndSet(null, FCArrayCell(FCArrayCellStates.ADD_OP, element))) {
                continue
            }
            // means we were able to publish operation, now let's try to get its result or become the combiner
            while (operationsArray[i].value!!.op != FCArrayCellStates.READY) {
                if (lock.tryLock()) {
                    combinerRoutine()
                    lock.unlock()
                }
            }
            // no one could use this cell and set it to null, since it definitely contains operation result
            operationsArray[i].getAndSet(null)
        }
    }
}