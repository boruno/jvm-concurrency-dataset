import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.withLock
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val numberOfThreads = 4 * Thread.activeCount()
    private val operations = atomicArrayOfNulls<Operation<E>>(numberOfThreads)
    private val lock = atomic(false)
    private fun tryLock(): Boolean {
        return lock.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        lock.compareAndSet(expect = true, update = false)
    }

    private fun giveToCombiner(op: Operation<E>): E? {
        var i = 0
        while (true) {
            if (operations[i].compareAndSet(null, op)) break
            i = (i + 1) % numberOfThreads
        }
        while (true) {
            val curOp = operations[i].value as Operation<E>
            if (curOp.type == OperationType.DONE && operations[i].compareAndSet(curOp, null))
                return curOp.value
            else {
                if (tryLock()) {
                    combiner().also { unlock() }
                }
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
        if (tryLock()) {
                val res = q.poll()
                combiner()
                return res.also { unlock() }
        } else {
            return giveToCombiner(Operation(OperationType.POLL, null))
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (tryLock()) {
                val res = q.peek()
                combiner()
                return res.also { unlock() }
        } else {
            return giveToCombiner(Operation(OperationType.POLL, null))
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (tryLock()) {
                q.add(element)
                combiner()
            unlock()
        } else {
            giveToCombiner(Operation(OperationType.POLL, null)) // TODO
        }
    }

    private enum class OperationType {
        ADD, PEEK, POLL, DONE
    }

    private class Operation<E>(val type: OperationType, val value: E?)
}
