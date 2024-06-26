import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.lang.Exception
import java.util.*
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = java.util.concurrent.locks.ReentrantLock()
    private val pendingOperations = atomicArrayOfNulls<Action<E>?>(PENDING_ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while(true) {
            if (lock.tryLock()) {
                val result = q.poll()
                performPendingOps()
                lock.unlock()
                return result
            } else {
                val pendingIndex = Random.nextInt(0, PENDING_ARRAY_SIZE);
                if (pendingOperations[pendingIndex].compareAndSet(null, Action(Operation.DEQUEUE, null)))
                {
                    while(true)
                    {
                        if (lock.tryLock())
                        {
                            val result = q.poll()
                            performPendingOps()
                            lock.unlock()
                            return result
                        }
                        else {
                            if (pendingOperations[pendingIndex].value!!.isDone.value)
                            {
                                val currentOp = pendingOperations[pendingIndex].value!!
                                val result = currentOp.result
                                pendingOperations[pendingIndex].compareAndSet(currentOp, null)
                                return result
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while(true) {
            if (lock.tryLock()) {
                q.add(element)
                performPendingOps()
                lock.unlock()
                return
            } else {
                val pendingIndex = Random.nextInt(0, PENDING_ARRAY_SIZE);
                if (pendingOperations[pendingIndex].compareAndSet(null, Action(Operation.ENQUEUE, element)))
                {
                    while(true)
                    {
                        if (lock.tryLock())
                        {
                            q.add(element)
                            performPendingOps()
                            lock.unlock()
                        }
                        else {
                            if (pendingOperations[pendingIndex].value!!.isDone.value)
                            {
                                val currentOp = pendingOperations[pendingIndex].value!!
                                pendingOperations[pendingIndex].compareAndSet(currentOp, null)
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun performPendingOps() {
        for (i in PENDING_ARRAY_SIZE - 1 downTo 0) {
            if (pendingOperations[i].value != null)
            {
                if (!pendingOperations[i].value!!.isDone.value) {
                    if (pendingOperations[i].value!!.action == Operation.ENQUEUE) {
                        q.add(pendingOperations[i].value!!.result)
                        if (!pendingOperations[i].value!!.isDone.compareAndSet(false, true))
                            throw Exception("Unsuccessfull CAS in combiner thread (ENQUEUE)")
                    }
                    if (pendingOperations[i].value!!.action == Operation.DEQUEUE) {
                        val result = q.poll()
                        pendingOperations[i].value!!.result = result
                        if (!pendingOperations[i].value!!.isDone.compareAndSet(false, true))
                            throw Exception("Unsuccessfull CAS in combiner thread (DEQUEUE)")
                    }
                }
            }
        }
    }
}

class Action<E>(actionRequest: Operation, argument: E?) {
    var result = argument
    val action = actionRequest
    val isDone = atomic(false)
}
enum class Operation {
    ENQUEUE, DEQUEUE
}

private const val PENDING_ARRAY_SIZE = 3
