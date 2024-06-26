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
        return tryLockOrAddTask(Operation(Poll))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return tryLockOrAddTask(Operation(Peek))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        tryLockOrAddTask(Operation(Add, element))
    }

    private fun tryLockOrAddTask(task: Operation<E>): E? {
        var index = rnd.nextInt(FC_ARRAY_SIZE)

        while (!fcArray[index].compareAndSet(null, task)) {
            index = rnd.nextInt(FC_ARRAY_SIZE)
            continue
        }

        while (true) {
            if (lock.tryLock()) {
                for (i in 0 until FC_ARRAY_SIZE) {
                    val opInArray = fcArray[i].value ?: continue

                    when ((opInArray as Operation<E>).op) {
                        Add -> q.add(opInArray.element)
                        Poll -> opInArray.element = q.poll()
                        Peek -> opInArray.element = q.peek()
                        Done -> {}
                    }
                    opInArray.op = Done
                }

                lock.unlock()
            }

            if (task.op == Done) {
                val res = task.element
                fcArray[index].value = null
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

data class Operation<E>(var op: OperationName, var element: E? = null)

const val FC_ARRAY_SIZE = 2
enum class TASK {
    POLL, PEEK, ADD
}
enum class STATUS {
    DONE, PROCESSING
}
