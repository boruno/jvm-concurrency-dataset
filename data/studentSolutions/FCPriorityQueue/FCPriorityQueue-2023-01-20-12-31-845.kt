import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Any?>(FC_ARRAY_SIZE)
    private val lock = ReentrantLock()
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
            if (lock.tryLock()) {
                for (i in 0 until FC_ARRAY_SIZE) {
                    val t = fcArray[i].value ?: continue

                    val tInArray = t as Task<E>

                    if (tInArray.status == STATUS.PROCESSING) {
                        var resultOfOp: E? = null
                        when (tInArray.operation) {
                            OPERATION.ADD -> q.add(tInArray.arg as E?)
                            OPERATION.PEEK -> resultOfOp = q.peek()
                            OPERATION.POLL -> resultOfOp = q.poll()
                        }
                        tInArray.status = STATUS.DONE
                        tInArray.arg = resultOfOp
                    }
                }

                lock.unlock()
            }

            if (task.status == STATUS.DONE) {
                val res = task.arg
                fcArray[index].getAndSet(null)
                return res
            }
        }
    }

}

sealed interface OperationName
object Add : OperationName
object Peek : OperationName
object Poll : OperationName
object Done : OperationName

class Task<E>(val operation: OPERATION, var status: STATUS, var arg: E?)

const val FC_ARRAY_SIZE = 2
enum class OPERATION {
    POLL, PEEK, ADD
}
enum class STATUS {
    DONE, PROCESSING
}
