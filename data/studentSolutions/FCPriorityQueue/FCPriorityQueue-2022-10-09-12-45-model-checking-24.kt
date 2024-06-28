import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val lock = ReentrantLock()
    private val q = PriorityQueue<E>()
    private val emptyTask = Task<E>(-1, null)
    private val stages = ArrayList<AtomicInteger>()
    private val tasks = Array(3) {emptyTask}
    private val results = HashMap<Int, E?>()

    init {
        stages.add(AtomicInteger(0))
        stages.add(AtomicInteger(0))
        stages.add(AtomicInteger(0))
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            lock.lock()
            val x = q.poll()
            lock.unlock()
            return x
        }
        // return process(0, null)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            lock.lock()
            val x = q.poll()
            lock.unlock()
            return x
        }
        // return process(1, null)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            lock.lock()
            q.add(element)
            lock.unlock()
        }
        // process(2, element)
    }

    private fun process(name : Int, element: E?) : E? {
        val task = Task(name, element)
        var index = 0
        for (i in 0..2) {
            if (stages[i].compareAndSet(0, 1)) {
                tasks[i] = task
                index = i
                break
            }
        }
        while (true) {
            if (stages[index].get() == 2) {
                val result = results[index]
                stages[index].set(0)
                lock.unlock()
                return result
            }
            if (lock.tryLock()) {
                for (i in 0..2) {
                    if (stages[i].get() == 1) {
                        val curTask = tasks[i]
                        if (curTask.name == 0) {
                            results[i] = q.poll()
                        } else if (curTask.name == 1) {
                            results[i] = q.peek()
                        } else {
                            q.add(task.element)
                        }
                        stages[i].set(2)
                    }
                }
                val result = results[index]
                stages[index].set(0)
                lock.unlock()
                return result
            }
        }
    }

    class Task<E>(val name: Int, val element: E?)

}