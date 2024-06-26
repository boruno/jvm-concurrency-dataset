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

    private fun flatCombining(command: () -> Any, operation: Operation<E>): E? {
        if (tryLock()) {
            val result = if (q.size > 0) command.invoke() else null
            support()
            unlock()
            return if (result is Boolean) null else result as E?
        } else {
            while (true) {
                val index = ThreadLocalRandom.current().nextInt(flatCombiningArray.size)
                if (flatCombiningArray[index].compareAndSet(null, operation)) {
                    while (true) {
                        val message = flatCombiningArray[index].value
                        if (message !== null) {
                            if (tryLock()) {
                                flatCombiningArray[index].compareAndSet(message, null)
                                val result =
                                    if (message.status === OperationStatus.DONE) message.element else command.invoke()
                                support()
                                unlock()
                                return if (result is Boolean) null else result as E?
                            }
                            if (message.status === OperationStatus.DONE) {
                                flatCombiningArray[index].compareAndSet(message, null)
                                return message.element
                            }
                        }
                    }
                }
            }
        }
    }

    private fun support() {
        for (i in 0 until flatCombiningArray.size) {
            if (flatCombiningArray[i].value === null) continue
            val currentOperation = flatCombiningArray[i].value
            val newOperation: Operation<E> = when (currentOperation!!.status) {
                OperationStatus.POLL -> Operation(OperationStatus.DONE, q.poll())
                OperationStatus.PEEK -> Operation(OperationStatus.DONE, q.peek())

                OperationStatus.ADD -> {
                    val element = currentOperation.element
                    q.add(element)
                    Operation(OperationStatus.DONE, element)
                }

                else -> continue
            }
            flatCombiningArray[i].value = newOperation
        }
    }

    private fun tryLock() = lock.compareAndSet(false, true)

    private fun unlock() = lock.getAndSet(false)

    private enum class OperationStatus {
        POLL, PEEK, ADD, DONE
    }

    private data class Operation<E>(val status: OperationStatus, val element: E? = null)
}