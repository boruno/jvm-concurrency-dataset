import java.util.*
import kotlinx.atomicfu.*
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private enum class OperationKind {
        POLL, PEEK, ADD
    }

    private data class Operation<E> (val kind: OperationKind, var element: E?)

    private val q = PriorityQueue<E>()
    private val locked: AtomicBoolean = atomic(false)
    private val ARRAY_SIZE = 10
    private val fc_array = atomicArrayOfNulls<Operation<E>?>(ARRAY_SIZE)

    private fun tryLock() = locked.compareAndSet(false, true)
    private fun unlock() {
        locked.value = false
    }

    private fun completeOperation(operation: Operation<E>?) {
        if (operation == null) return
        val kind = operation.kind
        if (kind == OperationKind.POLL) {
            operation.element = q.poll()
        } else if (kind == OperationKind.PEEK) {
            operation.element = q.peek()
        } else {
            q.add(operation.element)
        }

    }

    private fun workerCycle(operation: Operation<E>): E? {
        while (true) {
            val randomIndex = Random.nextInt(ARRAY_SIZE)
            if (!fc_array[randomIndex].compareAndSet(null, operation)) continue
            while (true) {
                if (!tryLock())  continue
                for (i in (0 until ARRAY_SIZE)) {
                    completeOperation(fc_array[i].value)
                }
                val result = fc_array[randomIndex].getAndSet(null)
                unlock()
                return result?.element 
            }

        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return workerCycle(Operation(OperationKind.POLL, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return workerCycle(Operation(OperationKind.PEEK, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        workerCycle(Operation(OperationKind.ADD, element))
    }
}