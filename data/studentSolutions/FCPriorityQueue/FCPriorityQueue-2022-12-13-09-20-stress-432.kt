import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

/**
 * @author Еров Егор
 */
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val buffer = atomicArrayOfNulls<OperationWithElement<E>?>(BUFFER_SIZE)
    private val lock = atomic(false)

    private class OperationWithElement<E>(element_: E?, operation_: OPERATION) {
        val element: E?
        val operation: OPERATION

        init {
            element = element_
            operation = operation_
        }
    }

    private fun tryLock() = lock.compareAndSet(expect = false, update = true)
    private fun unlock() {
        lock.value = false
    }

    private enum class OPERATION {
        ADD, POLL, PEEK, COMPLETED
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return processOperation(OperationWithElement(null, OPERATION.POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return processOperation(OperationWithElement(null, OPERATION.PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        processOperation(OperationWithElement(element, OPERATION.ADD))
    }

    private fun processOperation(operationWithElement: OperationWithElement<E>): E? {
        var index: Int
        while (true) {
            index = ThreadLocalRandom.current().nextInt(0, BUFFER_SIZE)
            if (buffer[index].compareAndSet(null, operationWithElement)) {
                break
            }
        }
        while (true) {
            if (tryLock()) {
                processCombiner()
                unlock()
            }
            val result = buffer[index].value
            if (result!!.operation == OPERATION.COMPLETED) {
                buffer[index].compareAndSet(result, null)
                return result.element
            }
        }
    }

    /**
     * Calls only when current thread is combiner.
     * Lookups array and compute queued operations.
     */
    private fun processCombiner() {
        var index = 0
        while (index != BUFFER_SIZE) {
            val operationWithElement = buffer[index].value
            if (operationWithElement != null) {
                when (operationWithElement.operation) {
                    OPERATION.POLL -> {
                        buffer[index].getAndSet(OperationWithElement(q.poll(), OPERATION.COMPLETED))
                    }
                    OPERATION.PEEK -> {
                        buffer[index].getAndSet(OperationWithElement(q.peek(), OPERATION.COMPLETED))
                    }
                    OPERATION.ADD -> {
                        q.add(operationWithElement.element)
                        buffer[index].getAndSet(OperationWithElement(null, OPERATION.COMPLETED))
                    }
                    else -> {
                        assert(false)
                    }
                }
            }
            ++index
        }
    }
}

private const val BUFFER_SIZE = 16
