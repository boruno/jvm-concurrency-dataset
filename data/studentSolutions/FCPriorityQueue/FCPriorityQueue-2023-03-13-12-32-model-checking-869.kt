import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlinx.atomicfu.locks.ReentrantLock

class Operation<E>(var op: OperationStatus, var element: E?)

enum class OperationStatus {
    ToPoll,
    ToPeek,
    ToAdd,
    Complete
}
class FCPriorityQueue<E : Comparable<E>> {
    val queue = PriorityQueue<E>()
    private val workers = 4 * Runtime.getRuntime().availableProcessors()
    private val orders = atomicArrayOfNulls<Operation<E>?>(workers)

    private val lock = ReentrantLock()

    private fun performOrder(operation: Operation<E>) : E? {
        while (true) {
            val randomIndex = ThreadLocalRandom.current().nextInt(0, workers)
            // places the order in
            if (orders[randomIndex].compareAndSet(null, operation)) {
                while (true) {
                    var returnVal: E? = null
                    if (lock.tryLock()) {
                        // search for a placed order
                        for (i in 0 until workers) {
                            val curOperation = orders[i].value ?: continue

                            // perform the operations
                            operation.element = when (curOperation.op) {
                                OperationStatus.ToPoll -> queue.poll()
                                OperationStatus.ToAdd -> {queue.add(curOperation.element); null}
                                OperationStatus.ToPeek -> queue.peek()
                                OperationStatus.Complete -> {
                                    null
                                }
                            }
                            curOperation.op = OperationStatus.Complete
                        }
                        lock.unlock()
                    }

                    if (operation.op == OperationStatus.Complete) {
                        orders[randomIndex].value = null
                        return operation.element
                    }
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return performOrder(Operation(OperationStatus.ToPoll, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return performOrder(Operation(OperationStatus.ToPeek, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        performOrder(Operation(OperationStatus.ToAdd, element))
    }


}