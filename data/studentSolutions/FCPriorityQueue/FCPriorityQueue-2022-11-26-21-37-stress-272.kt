import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.Nothing


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val operations = Array<AtomicReference<Operation<E>>>(OPERATIONS_BUFFER_SIZE) {
        AtomicReference(None())
    }


    private var locked = AtomicBoolean(false)
    fun tryWithLock(block: () -> Unit) {
        if (locked.compareAndSet(false, true)) {
            block()
            locked.set(false)
        }
    }

    private fun performOperation(operation: Operation<E>) {
        operation.setResult( when (operation) {
            is Add<E> -> {
                q.add(operation.value)
                Nothing()
            }
            is Peek -> Just(if (q.isNotEmpty()) { q.peek() } else {null} )
            is Poll -> Just(if (q.isNotEmpty()) { q.poll() } else {null} )
            else -> Nothing()
        })
    }

    private fun processLockedOperation(operation: Operation<E>) {
        performOperation(operation)
        for (operationInCell in operations) {
            val currentOperation = operationInCell.get()
            if (operationInCell.compareAndSet(currentOperation, None())) {
                performOperation(currentOperation)
            }
        }
    }

    fun mainCycle(operation: Operation<E>): OperationResult<E> {
        while (!operation.isPerformed) {
            val cellToPutOperation = operations[Random().nextInt(OPERATIONS_BUFFER_SIZE)]
            val operationInCell = cellToPutOperation.get()
            if (operationInCell !is None) {
                continue
            }
            if (cellToPutOperation.compareAndSet(operationInCell, operation)) {
                repeat(100) {
                    if (operation.isPerformed) {
                        return@repeat
                    }
                }
                if (operation.isPerformed) {
                    break
                }
                if (!cellToPutOperation.compareAndSet(operation, None())) {
                    break // 39 line
                }
            }

            tryWithLock {
                processLockedOperation(operation)
            }
        }
        return operation.result
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val performedResult = (mainCycle(Poll()) as Performed).result as Just<E>
        return performedResult.value
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val performedResult = (mainCycle(Peek()) as Performed).result as Just<E>
        return performedResult.value
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        mainCycle(Add(element))

    }
}

private const val OPERATIONS_BUFFER_SIZE = 5