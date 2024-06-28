import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.withLock
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val numberOfThreads = 4 * Thread.activeCount()
    private val operations = atomicArrayOfNulls<Operation<E>>(numberOfThreads)
    private val lock = ReentrantLock()

    private fun giveToCombiner(op: Operation<E>): Pair<Boolean,E?> {
        var ind = ThreadLocalRandom.current().nextInt(numberOfThreads)
        for (i in 0 until 1000) {
            if (operations[ind].compareAndSet(null, op)) {
                for (j in 0 until 100) {
                    val cop = operations[ind].value as Operation<E>
                    if (cop.type == OperationType.DONE && operations[ind].compareAndSet(cop, null)) {
                        return Pair(true, cop.value)
                    }
                }
                return if (operations[ind].compareAndSet(op, null)) {
                    Pair(false, null)
                } else {
                    val res = operations[ind].value?.value
                    operations[ind].value = null
                    Pair(true, res)
                }
            }
            ind++
            if (ind == numberOfThreads) {
                ind = 0
            }
        }
        return Pair(false, null)
    }

    private fun combiner() {
        for (i in 0 until numberOfThreads) {
            val op = operations[i].value ?: continue
            when (op.type) {
                OperationType.ADD -> {
                    if (operations[i].compareAndSet(op, Operation(OperationType.DONE, null))) q.add(op.value)
                }

                OperationType.PEEK -> operations[i].compareAndSet(op, Operation(OperationType.DONE, q.peek()))

                OperationType.POLL -> {
                    val res = q.poll()
                    if (!operations[i].compareAndSet(op, Operation(OperationType.DONE, res)) && res != null)
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
            return giveToCombiner(Operation(OperationType.POLL, null)).second
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
            return giveToCombiner(Operation(OperationType.PEEK, null)).second
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
                return
            }
        } else {
            giveToCombiner(Operation(OperationType.POLL, null))
            return
        }
    }

    private enum class OperationType {
        ADD, PEEK, POLL, DONE
    }

    private class Operation<E>(val type: OperationType, val value: E?)
}

