// import kotlinx.atomicfu.locks.ReentrantLock
// import kotlinx.atomicfu.locks.withLock
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger

class FCPriorityQueue<E : Comparable<E>> {
    // private val lock = ReentrantLock()
    private val arraySize = 10
    private val q = PriorityQueue<E>()
    // private val emptyTask = Task<E>(-1, null)
    private val stages = Array(arraySize) {AtomicInteger(0)}
    private val tasks = Array(arraySize) {Task<E>(-1, null)}
    private val results = Array(arraySize) {Task<E>(-1, null)}

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return process(0, null)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return process(1, null)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        process(2, element)
    }

    private fun process(name : Int, element: E?) : E? {
        val task = Task(name, element)
        var index = 0
        while (true) {
            if (stages[index].compareAndSet(0, 1)) {
                tasks[index] = task
                stages[index].set(2)
                break
            }
            index = (index + 1) % arraySize
        }
        while (true) {
            if (stages[index].compareAndSet(4, 5)) {
                val result = results[index].element
                stages[index].set(0)
                return result
            }
            synchronized(q) {
                for (i in 0 until arraySize) {
                    if (stages[i].compareAndSet(2, 3)) {
                        val curTask = tasks[i]
                        if (curTask.name == 0) {
                            results[i] = Task(-1, q.poll())
                        } else if (curTask.name == 1) {
                            results[i] = Task(-1, q.peek())
                        } else {
                            q.add(task.element)
                        }
                        stages[i].set(4)
                    }
                }
            }
        }
    }

    class Task<E>(val name: Int, val element: E?)

}
