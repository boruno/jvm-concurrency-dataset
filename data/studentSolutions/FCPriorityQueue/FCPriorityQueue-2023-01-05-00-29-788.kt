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
                for (i in 0 until flatCombiningArray.size) {
                    val message = flatCombiningArray[i].value ?: continue
                    when (message.status) {
                        OperationStatus.POLL -> flatCombiningArray[i].compareAndSet(
                            message,
                            Operation(OperationStatus.DONE, q.poll())
                        )

                        OperationStatus.PEEK -> flatCombiningArray[i].compareAndSet(
                            message,
                            Operation(OperationStatus.DONE, q.peek())
                        )

                        OperationStatus.ADD -> {
                            q.add(message.element)
                            flatCombiningArray[i].compareAndSet(message, Operation(OperationStatus.DONE))
                        }

                        else -> break
                    }
                }
                unlock()
                return result as E?
            } else {
                if (flatCombiningArray[index].compareAndSet(null, operation)) {
                    val currentOperation = flatCombiningArray[index].value
                    if (currentOperation != null && currentOperation.status == OperationStatus.DONE) {
                        flatCombiningArray[index].compareAndSet(currentOperation, null)
                        return currentOperation.element
                    }
                }
            }
        }
    }

    private fun tryLock() = lock.compareAndSet(false, true)

    private fun unlock() = lock.getAndSet(false)

    private enum class OperationStatus {
        POLL, PEEK, ADD, DONE
    }

    private data class Operation<E>(val status: OperationStatus, val element: E? = null)
}