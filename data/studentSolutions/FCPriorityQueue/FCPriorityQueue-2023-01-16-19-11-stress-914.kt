import java.util.*
import kotlinx.atomicfu.*
import java.util.concurrent.ThreadLocalRandom


class FCPriorityQueue<E : Comparable<E>> {
    private val threads = 5 * Runtime.getRuntime().availableProcessors()
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val operations = atomicArrayOfNulls<Operation>(threads)

    constructor() {
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var idx = ThreadLocalRandom.current().nextInt(0, threads)
        val operation = Operation({ q.poll() })
        while (!operations.get(idx).compareAndSet(null, operation)) {
            idx = ThreadLocalRandom.current().nextInt(0, threads)
        }
        do {
            if (lock.compareAndSet(false, true)) {
                for (i in 0 until threads) {
                    val op = operations.get(i)
                    val opValue = op.value
                    if (opValue != null && !opValue.done) {
                        opValue.call()
                    }
                }
                lock.compareAndSet(true, false)
                break
            }
        } while (!operation.done)
        do {
            if (lock.compareAndSet(false, true)) {
                for (i in 0 until threads) {
                    val op = operations.get(i)
                    val opValue = op.value
                    if (opValue != null && !opValue.done) {
                        opValue.call()
                    }
                }
                lock.compareAndSet(true, false)
                break
            }
        } while (!operation.done)
        operations.get(idx).getAndSet(null)
        return operation.operationResult as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var idx = ThreadLocalRandom.current().nextInt(0, threads)
        val operation = Operation({ q.peek() })
        while (!operations.get(idx).compareAndSet(null, operation)) {
            idx = ThreadLocalRandom.current().nextInt(0, threads)
        }
        do {
            if (lock.compareAndSet(false, true)) {
                for (i in 0 until threads) {
                    val op = operations.get(i)
                    val opValue = op.value
                    if (opValue != null && !opValue.done) {
                        opValue.call()
                    }
                }
                lock.compareAndSet(true, false)
                break
            }
        } while (!operation.done)
        operations.get(idx).getAndSet(null)
        return operation.operationResult as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var idx = ThreadLocalRandom.current().nextInt(0, threads)
        val operation = Operation({ q.add(element) })
        while (!operations.get(idx).compareAndSet(null, operation)) {
            idx = ThreadLocalRandom.current().nextInt(0, threads)
        }
        do {
            if (lock.compareAndSet(false, true)) {
                for (i in 0 until threads) {
                    val op = operations.get(i)
                    val opValue = op.value
                    if (opValue != null && !opValue.done) {
                        opValue.call()
                    }
                }
                lock.compareAndSet(true, false)
                break
            }
        } while (!operation.done)
        operations.get(idx).getAndSet(null)
        return
    }
}

private class Operation {
    public var operationResult: Any?
    public var done: Boolean
    val operation: () -> Any?

    constructor(operation: () -> Any?) {
        this.operation = operation
        this.done = false
        this.operationResult = null
    }

    public fun call() {
        operationResult = operation()
        done = true
    }
}
