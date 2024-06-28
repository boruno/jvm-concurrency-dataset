import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val fcArray = atomicArrayOfNulls<Any?>(ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return process { q.poll() } as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return process { q.peek() } as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        process { q.add(element) }
    }

    private fun process(task: () -> Any?): Any? {
        if (lock.tryLock()) {
            val result = task()
            processFcArrayTasks()
            lock.unlock()
            return result
        }
        var i: Int
        while (true) {
            i = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
            if (fcArray[i].compareAndSet(null, Operation(task))) {
                break
            }
        }
        while (true) {
            var result = fcArray[i].value
            if (result is Result) {
                fcArray[i].compareAndSet(result, null)
                return result.result
            }
            if (lock.tryLock()) {
                result = fcArray[i].value
                fcArray[i].compareAndSet(result, null)
                result = if (result is Result) result.result else (result as Operation).task()
                processFcArrayTasks()
                lock.unlock()
                return result
            }
        }


    }

    private fun processFcArrayTasks() {
        for (i in 0 until fcArray.size) {
            val task = fcArray[i].value
            if (task is Operation) {
                fcArray[i].compareAndSet(task, Result(task.task()))
            }
        }
    }

    private class Operation(val task: () -> Any?)
    private class Result(val result: Any?)
}


const val ARRAY_SIZE = 16