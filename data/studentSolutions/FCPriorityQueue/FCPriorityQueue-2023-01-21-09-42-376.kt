import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

sealed interface OperationName
object Add : OperationName
object Peek : OperationName
object Poll : OperationName
object Done : OperationName

data class Operation<E>(var op: OperationName, var element: E? = null)

class FCPriorityQueue<E : Comparable<E>> {
//    private val lock = ReentrantLock()
    val lock = atomic(false)
    private val rnd = Random()
    private val numberOfWorkers = 4 * Runtime.getRuntime().availableProcessors()
    private val operations = atomicArrayOfNulls<Operation<E>?>(numberOfWorkers)
    private val q = PriorityQueue<E>()

    fun tryLock(): Boolean {
        lock.compareAndSet(false, true)
        return lock.value
    }

    fun unlock() {
        lock.compareAndSet(true, false)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = operation(Operation(Poll), retrieveElement)

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = operation(Operation(Peek), retrieveElement)

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) = operation(Operation(Add, element)) { idx, _ -> operations[idx].value = null }

    private val retrieveElement: (Int, Operation<E>) -> E? = { idx: Int, worker: Operation<E> ->
        val res = worker.element
        operations[idx].value = null
        res
    }

    private fun <R> operation(operation: Operation<E>, ret: (Int, Operation<E>) -> R): R {
        while (true) {
            val randomIndex = rnd.nextInt(numberOfWorkers)
            if (operations[randomIndex].compareAndSet(null, operation)) {
                if (operations[randomIndex].value != operation) throw IllegalArgumentException("Wrong argument")
                while (true) {
                    if (tryLock()) {
                        for (i in 0 until numberOfWorkers) {
                            val currentOperation = operations[i].value ?: continue
                            when (currentOperation.op) {
                                Add -> q.add(currentOperation.element)
                                Poll -> currentOperation.element = q.poll()
                                Peek -> currentOperation.element = q.peek()
                                Done -> {}
                            }
                            currentOperation.op = Done
                        }
                        unlock()
                    }
                    if (operation.op == Done) {
                        return ret(randomIndex, operation)
                    }
                }
            }
        }
    }
}
