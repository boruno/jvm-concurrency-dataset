import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom.current
import java.util.concurrent.locks.ReentrantLock

enum class OperationType {
    POLL,
    PEEK,
    ADD,
    RESULT
}

class Operation<E>(val operationType: OperationType, val element: E? = null)

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val opArray = atomicArrayOfNulls<Operation<E>>(2 * Runtime.getRuntime().availableProcessors())
    private val lock = ReentrantLock()
    private val rand = current()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return tryPerform(Operation(OperationType.POLL, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return tryPerform(Operation(OperationType.PEEK, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        tryPerform(Operation(OperationType.ADD, element))
    }

    private fun perform(operation: Operation<E>): E? {
        return if (operation.operationType == OperationType.ADD) {
            q.add(operation.element)
            null
        } else if (operation.operationType == OperationType.PEEK) {
            q.peek()
        } else {
            q.poll()
        }
    }

    private fun tryPerform(operation: Operation<E>): E? {
        var arrIdx = -1
        while (true) {
            if (lock.tryLock()) {
                if (arrIdx != -1) {
                    opArray[arrIdx].value = null
                }
                val myRet = perform(operation)
                for (i in 0 until opArray.size) {
                    if (opArray[i].value != null && opArray[i].value!!.operationType != OperationType.RESULT) {
                        val ret = perform(opArray[i].value!!)
                        opArray[i].value = Operation(OperationType.RESULT, ret)
                    }
                }
                lock.unlock();
                return myRet
            } else {
                if (arrIdx == -1) {
                    val idx = rand.nextInt(Runtime.getRuntime().availableProcessors())
                    if (opArray[idx].compareAndSet(null, operation)) {
                        arrIdx = idx
                        continue
                    }
                } else {
                    if (opArray[arrIdx].value!!.operationType == OperationType.RESULT) {
                        val ret = opArray[arrIdx].value!!.element
                        opArray[arrIdx].value = null
                        return ret
                    }
                }
            }
        }
    }
}