import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.withLock
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val numberOfThreads = Thread.activeCount()
    private val operations = atomicArrayOfNulls<Operation<E>>(numberOfThreads)
    private val lock = ReentrantLock()

    private fun giveToCombiner(op: Operation<E>): E? {
        while (true) {
            for (i in 0 until numberOfThreads) {
                if (operations[i].compareAndSet(null, op)) {
                    while (true) {
                        val curOp = operations[i].value as Operation<E>
                        if (curOp.type == OperationType.DONE && operations[i].compareAndSet( curOp, null)) {
                            return curOp.value
                        }
                    }
                }
            }
        }
    }

    private fun combiner() {
        for (i in 0 until numberOfThreads) {
            val op = operations[i].value ?: continue
            when (op.type) {
                OperationType.ADD -> {
                    if (operations[i].compareAndSet(op, Operation(OperationType.DONE, null)))
                        q.add(op.value)
                }

                OperationType.PEEK -> operations[i].compareAndSet(op, Operation(OperationType.DONE, q.peek()))

                OperationType.POLL -> {
                    val res = q.poll()
                    if (!operations[i].compareAndSet(op, Operation(OperationType.DONE, null)) && res != null)
                        q.add(res)
                }

                OperationType.DONE -> {}
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (!lock.isLocked) {
            lock.withLock {
                val res = q.poll()
                combiner()
                return res
            }
        } else {
            return giveToCombiner(Operation(OperationType.POLL, null))
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (!lock.isLocked) {
            lock.withLock {
                val res = q.peek()
                combiner()
                return res
            }
        } else {
            return giveToCombiner(Operation(OperationType.POLL, null))
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (!lock.isLocked) {
            lock.withLock {
                q.add(element)
                combiner()
            }
        } else {
            giveToCombiner(Operation(OperationType.POLL, null))
        }
    }

    private enum class OperationType {
        ADD, PEEK, POLL, DONE
    }

    private class Operation<E>(val type: OperationType, val value: E?)
}
