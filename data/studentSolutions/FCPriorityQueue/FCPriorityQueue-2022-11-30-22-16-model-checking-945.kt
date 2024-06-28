import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcLock = reentrantLock()
    private val fcArray = Array<OperationCell<E>>(FC_ARRAY_SIZE, {_ -> OperationCell() })

    private fun processRequests() {
        fcArray.forEach { cell ->
            cell.lock.withLock {
                var newOp = cell.operation
                when (cell.operation) {
                    Operation.ADD -> {
                        q.add(cell.element!!)
                        newOp = Operation.EMPTY
                    }
                    Operation.PEEK -> {
                        cell.element = q.peek()
                        newOp = Operation.EMPTY
                    }
                    Operation.POLL -> {
                        cell.element = q.poll()
                        newOp = Operation.EMPTY
                    }
                    else -> {}
                }
                cell.operation = newOp
            }
            cell.cond.signal()
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (fcLock.tryLock()) {
            val result = q.poll()
            processRequests()
            fcLock.unlock()
            return result
        } else {
            val cell = fcArray.random()
            if (cell.lock.tryLock()) {
                if (cell.operation != Operation.EMPTY) {
                    cell.lock.unlock()
                } else {
                    cell.operation = Operation.POLL
                    cell.cond.await()
                    val element = cell.element
                    cell.lock.unlock()
                    return element
                }
            }
            fcLock.withLock {
                val result = q.poll()
                processRequests()
                return result
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (fcLock.tryLock()) {
            val result = q.peek()
            processRequests()
            fcLock.unlock()
            return result
        } else {
            val cell = fcArray.random()
            if (cell.lock.tryLock()) {
                if (cell.operation != Operation.EMPTY) {
                    cell.lock.unlock()
                } else {
                    cell.operation = Operation.PEEK
                    cell.cond.await()
                    val element = cell.element
                    cell.lock.unlock()
                    return element
                }
            }
            fcLock.withLock {
                val result = q.peek()
                processRequests()
                return result
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (fcLock.tryLock()) {
            q.add(element)
            processRequests()
            fcLock.unlock()
            return
        } else {
            val cell = fcArray.random()
            if (cell.lock.tryLock()) {
                if (cell.operation != Operation.EMPTY) {
                    cell.lock.unlock()
                } else {
                    cell.operation = Operation.PEEK
                    cell.element = element
                    cell.cond.await()
                    cell.lock.unlock()
                    return
                }
            }
            fcLock.withLock {
                q.add(element)
                processRequests()
                return
            }
        }
    }

    private enum class Operation {
        ADD,
        POLL,
        PEEK,
        EMPTY;
    }

    private class OperationCell<E> {
        val lock = reentrantLock()
        val cond = lock.newCondition()
        var operation = Operation.EMPTY
        var element: E? = null
    }
}

private val FC_ARRAY_SIZE = 1