import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Task<E>?>(FC_ARRAY_SIZE)
    private val locked = atomic(false)
    private val rnd = Random()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return tryLockOrAddTask(Task(OPERATION.POLL, STATUS.PROCESSING, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return tryLockOrAddTask(Task(OPERATION.PEEK, STATUS.PROCESSING, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        tryLockOrAddTask(Task(OPERATION.ADD, STATUS.PROCESSING, element))
    }

    private fun tryLockOrAddTask(task: Task<E>): E? {
        var index = rnd.nextInt(FC_ARRAY_SIZE)

        while (!fcArray[index].compareAndSet(null, task)) {
            index = rnd.nextInt(FC_ARRAY_SIZE)
            continue
        }

        while (true) {
            if (tryLock()) {
                processArray()
                unlock()
            }

            if (task.status == STATUS.DONE) {
                val result = task.arg
                fcArray[index].value = null
                return result
            }

        }
    }
    private fun processArray() {
        IntRange(0, FC_ARRAY_SIZE)
            .mapNotNull { idx -> fcArray[idx].value }
            .filter { task -> task.status == STATUS.PROCESSING }
            .map { task -> {
                when (task.operation) {
                    OPERATION.ADD -> q.add(task.arg)
                    OPERATION.PEEK -> task.arg = q.peek()
                    OPERATION.POLL -> task.arg = q.poll()
                }
                task.status = STATUS.DONE
            } }
    }

    private fun tryLock() = locked.compareAndSet(expect = false, update = true)

    private fun unlock() {
        locked.value = false
    }


}

class Task<E>(val operation: OPERATION, var status: STATUS, var arg: E?)

const val FC_ARRAY_SIZE = 8
enum class OPERATION {
    POLL, PEEK, ADD
}
enum class STATUS {
    DONE, PROCESSING
}
