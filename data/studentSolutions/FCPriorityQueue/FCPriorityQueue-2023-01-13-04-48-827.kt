import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val flatCombiningArray = atomicArrayOfNulls<Operation<E>?>(16)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return flatCombining(Operation(OperationStatus.POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return flatCombining(Operation(OperationStatus.PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        flatCombining(Operation(OperationStatus.ADD, element))
    }

    private fun flatCombining(operation: Operation<E>): E? {
        if (tryLock()) {
            val operationResult = executeOperation(operation)
            support()
            unlock()
            return operationResult
        } else {
            return null
        }
    }

    private fun executeOperation(operation: Operation<E>): E? = when (operation.status) {
        OperationStatus.POLL -> q.poll()
        OperationStatus.PEEK -> q.peek()
        OperationStatus.ADD -> q.add(operation.element).let { null }
        OperationStatus.DONE -> null
    }

    private fun support() {
        return
    }

    private fun tryLock() = lock.compareAndSet(false, true)

    private fun unlock() = lock.compareAndSet(true, false)

    private enum class OperationStatus {
        POLL, PEEK, ADD, DONE
    }

    private data class Operation<E>(val status: OperationStatus, val element: E? = null)
}