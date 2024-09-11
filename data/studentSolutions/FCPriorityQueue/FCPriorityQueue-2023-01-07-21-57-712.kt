import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
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
    fun poll(): E? = flatCombining(Operation(OperationStatus.POLL))

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = flatCombining(Operation(OperationStatus.PEEK))

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        flatCombining(Operation(OperationStatus.ADD, element))
    }

    private fun flatCombining(operation: Operation<E>): E? {
        while (true) {
            val index = ThreadLocalRandom.current().nextInt(flatCombiningArray.size)
            if (tryLock()) {
                val value = when (operation.status) {
                    OperationStatus.POLL -> q.poll()
                    OperationStatus.PEEK -> q.peek()
                    OperationStatus.ADD -> q.add(operation.element)
                    else -> { null }
                }

                for (i in 0 until flatCombiningArray.size) {
                    val message = flatCombiningArray[i].value ?: continue
                    when (message.status) {
                        OperationStatus.POLL -> {
                            val element = q.poll()
                            if (!flatCombiningArray[i].compareAndSet(
                                    message,
                                    Operation(OperationStatus.DONE, element)
                                )
                            ) {
                                q.add(element)
                            }
                        }

                        OperationStatus.PEEK -> {
                            flatCombiningArray[i].compareAndSet(
                                message,
                                Operation(OperationStatus.DONE, q.peek())
                            )
                        }

                        OperationStatus.ADD -> {
                            q.add(message.element)
                            flatCombiningArray[i].compareAndSet(message, Operation(OperationStatus.DONE))
                        }

                        else -> {
                            break
                        }
                    }
                }
                unlock()
                return value as E?
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