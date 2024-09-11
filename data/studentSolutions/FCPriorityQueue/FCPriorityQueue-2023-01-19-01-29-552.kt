import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import java.util.*

@Suppress("UNCHECKED_CAST")
class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val index = ThreadLocal.withInitial { getNext() }

    private fun getNext(): Int {
        while (true) {
            val x = threadCnt.value
            if (threadCnt.compareAndSet(x, x + 1))
                return x % THREAD_NUMBER
        }
    }

    private val arr = arrayOfNulls<Any>(THREAD_NUMBER)
    private val lock: AtomicBoolean = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val curID = index.get()
        arr[curID] = OperationType.POLL
        while (true) {
            if (arr[curID] != OperationType.POLL)
                break
            if (!lock.compareAndSet(false, true)) continue
            for (i in arr.indices) {
                when (arr[i]) {
                    OperationType.POLL -> arr[i] = queue.poll()
                    else -> continue
                }
            }
            lock.getAndSet(false)
        }
        return arr[curID] as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val curID = index.get()
        arr[curID] = OperationType.PEEK
        while (true) {
            if (arr[curID] != OperationType.PEEK)
                break
            if (!lock.compareAndSet(false, true)) continue
            for (i in arr.indices) {
                when (arr[i]) {
                    OperationType.PEEK -> arr[i] = queue.peek()
                    else -> continue
                }
            }
            lock.getAndSet(false)
        }
        return arr[curID] as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        /*abstractLockOperation {
            queue.add(element)
            null
        }*/
        val curID = index.get()
        arr[curID] = OperationType.ADD

        while (true) {
            if (arr[curID] != OperationType.ADD)
                break
            if (!lock.compareAndSet(false, true)) continue
            for (i in arr.indices) {
                when (arr[i]) {
                    OperationType.ADD -> {
                        arr[i] = queue.add(element)
                    }
                    else -> continue
                }
            }
            lock.getAndSet(false)
        }
        return
    }

    /*private fun getAction(op: OperationType, element: E?): () -> E? {
        return when (op) {
            OperationType.POLL -> queue.poll()
            OperationType.ADD -> queue.add(element)
            else -> queue.peek()
        }
    }*/
}

private enum class OperationType {POLL, PEEK, ADD}

private val threadCnt = atomic(0)
private const val THREAD_NUMBER = 3