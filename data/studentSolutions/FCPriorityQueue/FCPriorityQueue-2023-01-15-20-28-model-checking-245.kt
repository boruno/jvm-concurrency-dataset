import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val workers = Runtime.getRuntime().availableProcessors() * 8
    private val tasks = CombiningArray<E>(workers)
    private val lock = ReentrantLock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = doTask(Task( { q.poll() } )).value

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = doTask(Task( { q.peek() } )).value

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        doTask(
            Task({
                q.add(element)
                null
            })
        )
    }

    private fun doTask(task: Task<E>): Task<E> {
        tasks.add(task)
        while (true) {
            if (lock.tryLock()) {
                repeat(workers) {
//                    with(tasks.array[it]) {
//                        value?.run {
//                            perform()
//                            value = null
//                        }
//                    }
                    val taskToPerform = tasks.array[it].value
                    if (taskToPerform != null) {
                        taskToPerform.perform()
                        tasks.array[it].value = null
                    }
                }
                lock.unlock()
                return task
            }
            if (task.complete) {
                return task
            }
        }
    }

    private class CombiningArray<E>(size: Int) {
        val array = atomicArrayOfNulls<Task<E>>(size)
        private val random = ThreadLocalRandom.current()

        fun add(task: Task<E>) {
            var nextId = random.nextInt(array.size)
            while (true) {
                if (!array[nextId].compareAndSet(null, task)) {
                    nextId++
                    nextId %= array.size
                } else {
                    return
                }
            }
        }
    }

    private data class Task<E>(
        @Volatile var task: () -> E?,
        @Volatile var complete: Boolean = false,
        @Volatile var value: E? = null
    ) {

        fun perform() {
            value = task()
            complete = true
        }
    }
}