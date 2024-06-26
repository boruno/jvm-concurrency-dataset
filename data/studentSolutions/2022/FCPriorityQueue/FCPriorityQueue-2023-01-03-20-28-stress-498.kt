import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val arr = atomicArrayOfNulls<Pair<E?, Int>>(10)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return process(null, 1)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return process(null, 2)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        process(element, 3)
    }

    // fun getQ() : PriorityQueue<E> {
    //     return q
    // }

    private fun process(element: E?, mode: Int) : E? {
        var ind = 0
        while (true) {
            if (arr[ind].compareAndSet(null, Pair(element, mode))) {
                break
            }
            ind = (ind + 1) % 10
        }

        for (j in 0 until 100) {
            val pr = arr[ind].value ?: return null
            val status = pr.second
            if (status == 0) {
                arr[ind].getAndSet(null)
                return pr.first
            }

            if (lock.tryLock()) {
                try {
                    for (i in 0 until 10) {
                        val task = arr[ind].value ?: continue
                        if (task.second == 0) {
                            continue
                        }
                        val result = when (task.second) {
                            1 -> q.poll()
                            2 -> q.peek()
                            3 -> {q.add(task.first); return null}
                            else -> throw Error("unexpected status in task array: " + task.second)
                        }
                        // println(": $result")
                        arr[ind].getAndSet(Pair(result, 0))
                    }
                } finally {
                    lock.unlock()
                }
            }
        }
        return null
    }

}