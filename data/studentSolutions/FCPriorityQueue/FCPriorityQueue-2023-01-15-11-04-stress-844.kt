import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val arr = atomicArrayOfNulls<Operation<E>?>(8)

    private enum class OperationType { POLL, PEEK, ADD, DONE }
    private class Operation<E>(var type: OperationType, var value: E?)

    private fun tryLock() = lock.compareAndSet(false, true)
    private fun unlock() = lock.getAndSet(false)

    private fun combine(op: Operation<E>): E? {
        while (true) {
            val index = ThreadLocalRandom.current().nextInt(8)
            if (arr[index].compareAndSet(null, op)) {
                if (tryLock()) {
                    for (i in 0 until 8) {
                        val currOp = arr[i].value ?: continue
                        when (currOp.type) {
                            OperationType.ADD -> q.add(currOp.value)
                            OperationType.POLL -> currOp.value = q.poll()
                            OperationType.PEEK -> currOp.value = q.peek()
                            OperationType.DONE -> {}
                        }
                        currOp.type = OperationType.DONE
                    }
                }
                unlock()
            }
            if (op.type == OperationType.DONE){
                val res = op.value
                arr[index].value = null
                return res
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return combine(Operation(OperationType.POLL, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return combine(Operation(OperationType.PEEK, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
       combine(Operation(OperationType.ADD, element))
    }
}