import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val numberOfWorkers = 4 * Runtime.getRuntime().availableProcessors()
    private val lock : AtomicBoolean = atomic(false)
    private val q = PriorityQueue<E>()
    private val operations = atomicArrayOfNulls<Operation<E>>(numberOfWorkers)
    private val random = Random()
    private val PROCESS = Operation<E>(OperationsName.PROCESS, null)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return abstractOp(Operation(OperationsName.POLL, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return abstractOp(Operation(OperationsName.PEEK, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        abstractOp(Operation(OperationsName.ADD, element))
    }

    fun abstractOp(operation : Operation<E>) : E? {
        if (tryLock()) {
            val res = execByOp(operation)
            walkByOperations()
            unlock()
            return res
        } else {
            while (true) {
                val idx = random.nextInt(numberOfWorkers)
                if (operations[idx].compareAndSet(null, operation)) {
                    while (true) {
                        val res = operations[idx].value
                        if (res != null) {
                            if (res.name == OperationsName.RESULT && operations[idx].compareAndSet(res, null)) {
                                return res.value
                            }
                        }
                        if (tryLock()) {
//                            val res1 = operations[idx].value
//                            if (res1 != null) {
//                                if (res1.name == OperationsName.RESULT && operations[idx].compareAndSet(res1, null)) {
//                                    return res1.value
//                                }
//                            }
                            val result = execByOp(operation)
                            walkByOperations()
                            unlock()
                            return result
                        }
                    }
                }
            }
        }
    }

    private fun walkByOperations() {
        for (i in (0 until numberOfWorkers)) {
            if (operations[i].value != null) {
                val op = operations[i].value
                if (op != null) {
                    if (op.name == OperationsName.ADD &&
                        operations[i].compareAndSet(op, PROCESS)) {

                        q.add(op.value)
                        operations[i].compareAndSet(PROCESS, Operation(OperationsName.RESULT, null))
                        break

                    } else if (op.name == OperationsName.POLL &&
                        operations[i].compareAndSet(op, PROCESS)) {

                        operations[i].compareAndSet(PROCESS, Operation(OperationsName.RESULT, q.poll()))
                        break

                    } else if (op.name == OperationsName.PEEK &&
                        operations[i].compareAndSet(op, PROCESS)) {

                        operations[i].compareAndSet(PROCESS, Operation(OperationsName.RESULT, q.peek()))
                        break
                    }
                }
            }
        }
    }

    fun execByOp(operation : Operation<E>) : E? {
        when (operation.name) {
            OperationsName.ADD -> {
                q.add(operation.value)
            }
            OperationsName.PEEK -> {
                return q.peek()
            }
            OperationsName.POLL -> {
                return q.poll()
            }

            else -> {return null}
        }
        return null
    }


    private fun tryLock() : Boolean {
        return lock.compareAndSet(false, true)
    }

    private fun unlock() {
        lock.value = false
    }
}

enum class OperationsName {
    POLL, PEEK, ADD, PROCESS, RESULT
}

class Operation<E>(val name : OperationsName, val value : E?) {
}