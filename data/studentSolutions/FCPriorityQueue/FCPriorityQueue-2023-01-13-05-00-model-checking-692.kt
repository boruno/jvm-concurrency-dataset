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
        while (true) {
            val index = ThreadLocalRandom.current().nextInt()
            if (tryLock()) {
                flatCombiningArray[index].compareAndSet(operation, null)
                val operationResult = executeOperation(operation)
                support()
                unlock()
                return operationResult
            } else {
                flatCombiningArray[index].compareAndSet(null, operation)
                val value = flatCombiningArray[index].value
                if (value !== null) {
                    if (value.status === OperationStatus.DONE) {
                        flatCombiningArray[index].compareAndSet(operation, null)
                        return value.element
                    }
                }
            }
        }
    }

    private fun executeOperation(operation: Operation<E>): E? = when (operation.status) {
        OperationStatus.POLL -> q.poll()
        OperationStatus.PEEK -> q.peek()
        OperationStatus.ADD -> q.add(operation.element).let { null }
        OperationStatus.DONE -> null
    }

    private fun support() {
        for (i in 0 until flatCombiningArray.size) {
            val operation = flatCombiningArray[i].value ?: continue
            when (operation.status) {
                OperationStatus.POLL -> {
                    val result = q.poll()
                    flatCombiningArray[i].compareAndSet(operation, Operation(OperationStatus.DONE, result))
                }

                OperationStatus.PEEK -> {
                    val result = q.peek()
                    flatCombiningArray[i].compareAndSet(operation, Operation(OperationStatus.DONE, result))
                }

                OperationStatus.ADD -> {
                    q.add(operation.element)
                    flatCombiningArray[i].compareAndSet(operation, Operation(OperationStatus.DONE))
                }

                OperationStatus.DONE -> continue
            }
        }
    }

    private fun tryLock() = lock.compareAndSet(false, true)

    private fun unlock() = lock.compareAndSet(true, false)

    private enum class OperationStatus {
        POLL, PEEK, ADD, DONE
    }

    private data class Operation<E>(val status: OperationStatus, val element: E? = null)
}