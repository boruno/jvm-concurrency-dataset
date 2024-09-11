import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.IllegalStateException
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private var locked = AtomicBoolean(false)
    private val fc_array_size = 4 * Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    private val fc_array = atomicArrayOfNulls<Operation<E>?>(fc_array_size)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val operation = PollOperation<E>()
        tryCombiner(operation)
        return operation.result
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val operation = PeekOperation<E>()
        tryCombiner(operation)
        return operation.result
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val operation = AddOperation<E>(element)
        tryCombiner(operation)
    }

    private fun tryCombiner(operation: Operation<E>) {
        while (true) {
            val index = ThreadLocalRandom.current().nextInt(fc_array_size)

            if (fc_array[index].compareAndSet(null, operation)) {
                while (true) {
                    if (tryLock()) {
                        workAsCombiner()
                        unlock()
                        break
                    }
                }

                if (operation.finished.get()) {
                    return
                }
            }
        }
    }

    private fun workAsCombiner() {
        for (i in 0 until fc_array_size) {
            var current_operation: Operation<E> = fc_array[i].value ?: continue
            when (current_operation) {
                is PollOperation<*> -> {
                    current_operation = current_operation as PollOperation<E>
                    current_operation.result = q.poll()
                    current_operation.finished.compareAndSet(false, true)
                }

                is PeekOperation<*> -> {
                    current_operation = current_operation as PeekOperation<E>
                    current_operation.result = q.peek()
                    current_operation.finished.compareAndSet(false, true)
                }

                is AddOperation<*> -> {
                    current_operation = current_operation as AddOperation<E>
                    q.add(current_operation.element)
                    current_operation.finished.compareAndSet(false, true)
                }

                else -> {
                    throw IllegalStateException("No other types of operations are expected")
                }
            }
            fc_array[i].value = null
        }
    }

    private fun tryLock() = locked.compareAndSet(false, true)

    private fun unlock() = locked.compareAndSet(true, false)

    private open class Operation<E> {
        var result: E? = null
        var finished = AtomicBoolean(false)
    }

    private class PollOperation<E> : Operation<E>() {

    }

    private class PeekOperation<E> : Operation<E>() {

    }

    private class AddOperation<E>(element: E) : Operation<E>() {
        val element: E? = element
    }

}