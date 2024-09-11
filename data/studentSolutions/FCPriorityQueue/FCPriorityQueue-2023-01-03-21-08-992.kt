import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.PriorityQueue
import java.util.concurrent.ThreadLocalRandom
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

    private fun process(element: E?, mode: Int) : E? {
        if (mode > 0) {
            return null
        }
        var ind = ThreadLocalRandom.current().nextInt(10)
        while (true) {
            if (arr[ind].compareAndSet(null, Pair(element, mode))) {
                break
            }
            ind = (ind + 1) % 10
        }

        while (true) {
            val pr = arr[ind].value ?: return null
            if (pr.second == 0) {
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
                        arr[ind].compareAndSet(task, Pair(result, 0))
                    }
                } finally {
                    lock.unlock()
                }
            }
        }
    }

}