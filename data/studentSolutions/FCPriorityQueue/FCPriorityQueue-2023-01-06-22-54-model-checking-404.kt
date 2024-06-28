import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private enum class TaskType {
        POLL,
        PEEK,
        ADD,
        ANS,
    }

    private class Task<E>(val type: TaskType, val element: E? = null)

    private val fcArray = atomicArrayOfNulls<Task<E>>(ARRAY_LENGTH)
    private val q = PriorityQueue<E>()
    private val locked = atomic(false)

    private fun tryLock() = locked.compareAndSet(expect = false, update = true)
    private fun unlock() {
        locked.value = false
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val task = waitUnlock(Task(TaskType.POLL))
        val ans: E?
        if (task.type != TaskType.ANS) {
            ans = q.poll()
            checkTasks()
            unlock()
        } else {
            ans = task.element
        }
        return ans
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val task = waitUnlock(Task(TaskType.PEEK))
        val ans: E?
        if (task.type != TaskType.ANS) {
            ans = q.peek()
            checkTasks()
            unlock()
        } else {
            ans = task.element
        }
        return ans
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val task = waitUnlock(Task(TaskType.ADD, element))
        if (task.type != TaskType.ANS) {
            q.add(element)
            checkTasks()
            unlock()
        }
    }

    private fun checkTasks() {
        for (i in 0 until ARRAY_LENGTH) {
            val curTask = fcArray[i].value
            if (curTask != null) {
                var ans: E? = null
                when (curTask.type) {
                    TaskType.PEEK -> ans = q.peek()
                    TaskType.POLL -> ans = q.poll()
                    TaskType.ADD -> q.add(curTask.element)
                    else -> continue
                }
                fcArray[i].compareAndSet(curTask, Task(TaskType.ANS, ans))
            }
        }
    }

    private fun waitUnlock(task: Task<E>): Task<E> {
        val random = ThreadLocalRandom.current()
        var waiting = false
        var idx = random.nextInt(ARRAY_LENGTH)

        while (!tryLock()) {
            if (!waiting && fcArray[idx].compareAndSet(null, task)) {
                waiting = true
                if (fcArray[idx].value?.type == TaskType.ANS) break
            } else {
                idx = random.nextInt(ARRAY_LENGTH)
            }
        }

        val res: Task<E>
        if (waiting) {
            res = fcArray[idx].value!!
            fcArray[idx].compareAndSet(task, null)
        } else {
            res = task
        }
        return res
    }
}

private const val ARRAY_LENGTH = 20
