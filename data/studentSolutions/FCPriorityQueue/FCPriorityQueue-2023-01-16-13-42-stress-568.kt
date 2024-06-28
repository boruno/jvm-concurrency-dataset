import java.util.*
import kotlinx.atomicfu.*
import java.util.concurrent.ThreadLocalRandom


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val operations = atomicArrayOfNulls<Int>(10 * Runtime.getRuntime().availableProcessors())
    private val results = atomicArrayOfNulls<E?>(10 * Runtime.getRuntime().availableProcessors())

    constructor() {
        for (i in 0 until 10 * Runtime.getRuntime().availableProcessors()) {
            operations.get(i).getAndSet(0)
        }
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return null
        // var idx = setOperationIdx(1)
        // while (true) {
        //     if (lock.compareAndSet(false, true)) {
        //         val result = getResultIfReady(idx)
        //         var resultE: E? = result.second
        //         if (result.first == false) {
        //             resultE = q.poll()
        //             help(idx)
        //         }
        //         lock.getAndSet(false)
        //         return resultE
        //     } else {
        //         val result = getResultIfReady(idx)
        //         if (result.first != false) {
        //             return result.second
        //         }
        //     }
        //     Thread.yield()
        // }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return null
        // var idx = setOperationIdx(2)
        // while (true) {
        //     if (lock.compareAndSet(false, true)) {
        //         val result = getResultIfReady(idx)
        //         var resultE: E? = result.second
        //         if (result.first == false) {
        //             resultE = q.peek()
        //             help(idx)
        //         }
        //         lock.getAndSet(false)
        //         return resultE
        //     } else {
        //         val result = getResultIfReady(idx)
        //         if (result.first != false) {
        //             return result.second
        //         }
        //     }
        //     Thread.yield()
        // }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        return
        // var idx = setOperationIdx(3)
        // results.get(idx).getAndSet(element)
        // while (true) {
        //     if (lock.compareAndSet(false, true)) {
        //         val result = getResultIfReady(idx)
        //         if (result.first == false) {
        //             q.add(element)
        //             help(idx)
        //         }
        //         lock.getAndSet(false)
        //         return
        //     } else {
        //         val result = getResultIfReady(idx)
        //         if (result.first != false) {
        //             return
        //         }
        //     }
        //     Thread.yield()
        // }
    }

    private fun setOperationIdx(operationIdx: Int): Int {
        // val idx = ThreadLocalRandom.current().nextInt(0, 10 * Runtime.getRuntime().availableProcessors())
        for (i in 0 until 10 * Runtime.getRuntime().availableProcessors()) {
            if (operations.get(i).compareAndSet(null, operationIdx)) {
                return i
            }
        }
        return -1
    }

    private fun getResultIfReady(idx: Int): Pair<Boolean, E?> {
        if (operations.get(idx).value != 4) {
            return Pair(false, null);
        }
        operations.get(idx).getAndSet(0)
        return Pair(true, results.get(idx).getAndSet(null));
    }

    private fun help(idx: Int) {
        operations.get(idx).getAndSet(0)
        results.get(idx).getAndSet(null)
        for (i in 0 until 10 * Runtime.getRuntime().availableProcessors()) {
            val value = operations.get(i).value
            if (value == 0) {
                continue
            } else if (value == 1) {
                results.get(i).getAndSet(q.poll())
            } else if (value == 2) {
                results.get(i).getAndSet(q.peek())
            } else if (value == 3) {
                val result = results.get(i).value
                if (result != null) {
                    q.add(result)
                } else {
                    continue
                }
            } else if (value == 4) {
                continue
            }
            operations.get(i).getAndSet(4)
        }
        lock.getAndSet(false)
    }
}
