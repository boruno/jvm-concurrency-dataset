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

    private fun giveToCombiner(op: Operation<E>, e : E?): E? {
        if (!lock.isLocked) {
            lock.withLock {
                var res : E? = null
                when (op.type) {
                    OperationType.ADD -> q.add(e)
                    OperationType.PEEK -> res = q.peek()
                    OperationType.POLL -> res = q.poll()
                    OperationType.DONE -> {}
                }
                combiner()
                return res
            }
        }

        var i = ThreadLocalRandom.current().nextInt(numberOfThreads)
        while (true) {
            if (operations[i].compareAndSet(null, op)) break
            i = (i + 1) % numberOfThreads
        }
        while (true) {
            val curOp = operations[i].value as Operation<E>
            if (curOp.type == OperationType.DONE) {
                operations[i].compareAndSet(curOp, null)
                return curOp.value
            } else {
                lock.withLock { combiner() }
            }
        }
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

            return giveToCombiner(Operation(OperationType.POLL, null), null)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {

            return giveToCombiner(Operation(OperationType.POLL, null), null)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {

            giveToCombiner(Operation(OperationType.POLL, null), element) // TODO

    }

    private enum class OperationType {
        ADD, PEEK, POLL, DONE
    }

    private class Operation<E>(val type: OperationType, val value: E?)
}
