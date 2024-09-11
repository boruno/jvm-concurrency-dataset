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
        if (tryLock()) {
            val result = command.invoke()
            support()
            unlock()
            return if (result is Boolean) null else result as E?
        } else {
            val index = ThreadLocalRandom.current().nextInt(flatCombiningArray.size)
            while (true) if (flatCombiningArray[index].compareAndSet(null, operation)) break
            while (true) {
                val action = flatCombiningArray[index].value

                if (action !== null) {
                    if (action.status === OperationStatus.DONE) {
                        flatCombiningArray[index].compareAndSet(action, null)
                        return action.element
                    }
                }
                if (tryLock()) {
                    flatCombiningArray[index].compareAndSet(action, null)
                    if (action !== null) {
                        return if (action.status === OperationStatus.DONE) {
                            support()
                            unlock()
                            action.element
                        } else {
                            val result = command.invoke()
                            support()
                            unlock()
                            if (result is Boolean) null else result as E?
                        }
                    }
                }
            }
        }
    }

    private fun support() {
        for (i in 0 until flatCombiningArray.size) {
            val currentOperation = flatCombiningArray[i].value ?: continue
            when (currentOperation.status) {
                OperationStatus.POLL -> {
                    val operation = Operation(OperationStatus.DONE, q.poll())
                    if (!flatCombiningArray[i].compareAndSet(currentOperation, operation)) {
                        q.add(operation.element)
                    }
                }

                OperationStatus.PEEK -> {
                    val operation = Operation(OperationStatus.DONE, q.peek())
                    if (flatCombiningArray[i].compareAndSet(currentOperation, operation)) continue
                }

                OperationStatus.ADD -> {
                    val element = currentOperation.element
                    q.add(element)
                    val operation = Operation(OperationStatus.DONE, element)
                    if (flatCombiningArray[i].compareAndSet(currentOperation, operation)) continue
                }

                else -> continue
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