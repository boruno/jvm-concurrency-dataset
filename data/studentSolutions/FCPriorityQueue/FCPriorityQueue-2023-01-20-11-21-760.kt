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
        return tryLockOrAddTask(TASK.POLL)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return tryLockOrAddTask(TASK.PEEK)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        tryLockOrAddTask(TASK.ADD, element)
    }

    private fun tryLockOrAddTask(task: TASK, arg: E? = null): E? {
        val index = rnd.nextInt(FC_ARRAY_SIZE)
        while (!fcArray[index].compareAndSet(null, Operation(task, STATUS.PROCESSING, arg)))

        while (true) {
            if (lock.tryLock()) {
                for (i in 0 until FC_ARRAY_SIZE) {
                    if (fcArray[i].value == null) {
                        continue
                    }

                    val opInArray = fcArray[i].value as Operation

                    if (opInArray.status == STATUS.PROCESSING) {
                        var resultOfOp: E? = null
                        when (opInArray.task) {
                            TASK.ADD -> q.add(opInArray.arg as E)
                            TASK.PEEK -> resultOfOp = q.peek()
                            TASK.POLL -> resultOfOp = q.poll()
                        }
                        opInArray.status = STATUS.DONE
                        opInArray.arg = resultOfOp
                        fcArray[i].getAndSet(opInArray)
                    }
                }

                lock.unlock()
            }

            val op = fcArray[index].value as Operation
            if (op.status == STATUS.DONE) {
                fcArray[index].getAndSet(null)
                return op.arg as E?
            }
        }

        return 555 as E?;
    }

}

class Operation(val task: TASK, var status: STATUS, var arg: Any?)

const val FC_ARRAY_SIZE = 64
enum class TASK {
    POLL, PEEK, ADD
}
enum class STATUS {
    DONE, PROCESSING
}
