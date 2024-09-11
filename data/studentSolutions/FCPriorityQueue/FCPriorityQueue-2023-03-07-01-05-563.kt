import FCPriorityQueue.OperationType.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val threads = Thread.activeCount()
    private val flatCombining = atomicArrayOfNulls<Operation>(threads * 2)
    private val locked = atomic(false)
    private val random = ThreadLocalRandom.current()

    private enum class OperationType {
        POLL, PEEK, ADD, DONE
    }

    private inner class Operation(var type: OperationType, var value: E? = null) {
        fun getOperationResult(): E? = when (type) {
            POLL -> q.poll()
            PEEK -> q.peek()
            ADD -> {
                q.add(value)
                null
            }
            DONE -> null
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = Operation(POLL).execute()

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = Operation(PEEK).execute()

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        Operation(ADD).execute()
    }

    private fun Operation.execute(): E? {
        while (true) {
            val operationResult: E?

            if (type == DONE) return value

            if (tryLock()) {
                operationResult = getOperationResult()
                for (fcPosition in 0 until flatCombining.size) {
                    val curOperation = flatCombining[fcPosition].value ?: continue

                    curOperation.apply {
                        value = execute()
                        type = DONE
                    }
                }

                unlock()
                return operationResult
            }

            var fcEmptyIndex = random.nextInt(flatCombining.size)

            while (!flatCombining[fcEmptyIndex].compareAndSet(null, this)) {
                fcEmptyIndex = (fcEmptyIndex + 1) % flatCombining.size
            }
        }
    }

    private fun tryLock() = locked.compareAndSet(expect = false, update = true)
    private fun unlock() = locked.compareAndSet(expect = true, update = false)
}