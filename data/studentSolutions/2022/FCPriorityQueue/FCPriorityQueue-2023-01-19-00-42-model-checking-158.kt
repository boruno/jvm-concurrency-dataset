import java.util.*
import kotlinx.atomicfu.*
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val operationsSz = 4 * Thread.activeCount()
    private val operations = atomicArrayOfNulls<Pair<E?, Operation>?>(operationsSz)

    private val WAIT = 20

    private enum class Operation {
        POLL, PEEK, ADD, COMPLETE
    }

    // private class Pair<E>(val element: E?, val operation: Operation)

    private fun completeAllOperations() {
        for (i in (0 until operationsSz)) {
            val curOperation = operations[i].value

            if (curOperation == null) {
                continue
            }

            if (curOperation.second == Operation.POLL) {
                val ans = q.poll()
                if (!operations[i].compareAndSet(curOperation!!, Pair(ans, Operation.COMPLETE))) {
                    if (ans != null) {
                        q.add(ans)
                    }
                }
            } else if (curOperation.second == Operation.PEEK) {
                operations[i].compareAndSet(curOperation!!, Pair(q.peek(), Operation.COMPLETE))
            } else if (curOperation.second == Operation.ADD) {
                if (operations[i].compareAndSet(curOperation!!, Pair(null, Operation.COMPLETE))) {
                    q.add(curOperation.first)
                }
            }
        }
    }

    private fun addOperation(operation: Pair<E?, Operation>): Pair<Boolean, E?> {
        for (i in (0 until operationsSz)) {
            if (operations[i].compareAndSet(null, operation)) {
                for (it in (0 until WAIT)) {
                    val curOperation = operations[i].value!!
                    if (curOperation.second == Operation.COMPLETE && operations[i].compareAndSet(curOperation, null)) {
                        return Pair(true, curOperation.first)
                    }
                }

                if (operations[i].compareAndSet(operation, null)) {
                    return Pair(false, null)
                } else {
                    val curOperation = operations[i].value!!
                    operations[i].compareAndSet(curOperation, null)
                    return Pair(true, curOperation.first)
                }
            }
        }

        return Pair(false, null)
    }

    private fun makeOperation(operation: Pair<E?, Operation>): E? {
        while (true) {
            if (lock.compareAndSet(false, true)) {
                var ans: E? = null

                if (operation.second == Operation.POLL) {
                    ans = q.poll()
                } else if (operation.second == Operation.PEEK) {
                    ans = q.peek()
                } else {
                    q.add(operation.first!!)
                }

                completeAllOperations()
                lock.compareAndSet(true, false)

                return ans
            } else {
                val ans = addOperation(operation)

                if (ans.first) {
                    return ans.second
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return makeOperation(Pair(null, Operation.POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return makeOperation(Pair(null, Operation.PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        makeOperation(Pair(element, Operation.ADD))
    }
}