import kotlinx.atomicfu.atomic
import java.util.PriorityQueue
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

class FCPriorityQueue<E : Comparable<E>> {
    private val random = Random()
    private val lock = atomic(false)
    private val q = PriorityQueue<E>()
    private val emptyTask = Task<E>(-1, null)
    private val stages = Array(10) {AtomicInteger(0)}
    private val tasks = Array(10) {emptyTask}
    private val results = HashMap<Int, E>()

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
        var index = random.nextInt(0, 10)
        for (i in 0..9) {
            if (stages[(index + i) % 10].compareAndSet(0, 1)) {
                index = (index + i) % 10
                tasks[index] = task
                break
            }
        }
        while (true) {
            if (stages[index].get() == 2) {
                val result = results[index]
                stages[index].getAndSet(0)
                return result
            }
            if (lock.compareAndSet(false, true)) {
                for (i in 0..9) {
                    if (stages[index].get() == 1) {
                        val curTask = tasks[index]
                        if (curTask.name == 0) {
                            results[i] = q.peek()
                        } else if (curTask.name == 1) {
                            results[i] = q.poll()
                        } else {
                            q.add(task.element)
                        }
                        stages[index].getAndSet(2)
                    }
                }
                lock.getAndSet(false)
            }
        }
    }

    class Task<E>(val name: Int, val element: E?)

}