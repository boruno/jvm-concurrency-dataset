import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = AtomicBoolean(false)
    private val flatCombiningArray = atomicArrayOfNulls<Operation<E>>(10)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return flatCombining({ q.poll() }, Operation(OperationStatus.POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return flatCombining({ q.peek() }, Operation(OperationStatus.PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        flatCombining({ q.add(element) }, Operation(OperationStatus.ADD, element))
    }

    private fun flatCombining(command: () -> Any?, operation: Operation<E>): E? {
        while (true) {
            val index = Random.nextInt(flatCombiningArray.size)
            if (tryLock()) {
                val result = command()
                combineOperations()
                unlock()
                return result as E?
            } else {
                if (flatCombiningArray[index].compareAndSet(null, operation)) {
                    val currentOperation = flatCombiningArray[index].value
                    if (currentOperation != null) {
                        if (currentOperation.status == OperationStatus.DONE) {
                            flatCombiningArray[index].compareAndSet(currentOperation, null)
                            return currentOperation.element
                        }
                    }
                }
            }
        }
    }

    private fun combineOperations() {
        for (i in 0 until flatCombiningArray.size) {
            val operation = flatCombiningArray[i].value
            if (operation != null) {
                when (operation.status) {
                    OperationStatus.POLL -> flatCombiningArray[i].compareAndSet(
                        operation,
                        Operation(OperationStatus.DONE, q.poll())
                    )

                    OperationStatus.PEEK -> flatCombiningArray[i].compareAndSet(
                        operation,
                        Operation(OperationStatus.DONE, q.peek())
                    )

                    OperationStatus.ADD -> {
                        q.add(operation.element!!)
                        flatCombiningArray[i].compareAndSet(operation, Operation(OperationStatus.DONE))
                    }

                    OperationStatus.DONE -> flatCombiningArray[i].compareAndSet(operation, null)
                }
            }
        }
    }

    private fun tryLock() = lock.compareAndSet(false, true)

    private fun unlock() = lock.set(false)

    private enum class OperationStatus {
        POLL, PEEK, ADD, DONE
    }

    private data class Operation<E>(val status: OperationStatus, val element: E? = null)
}