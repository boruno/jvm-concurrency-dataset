import kotlinx.atomicfu.atomic
import java.util.*

@Suppress("UNCHECKED_CAST")
class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val index = ThreadLocal.withInitial { getNext() }

    private fun getNext(): Int {
        return if (threadCnt.value >= THREAD_NUMBER - 1) {
            threadCnt.getAndIncrement()
        } else {
            threadCnt.getAndIncrement() % THREAD_NUMBER
        }
    }
    private val arr = arrayOfNulls<Any>(THREAD_NUMBER)
    private val lock = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return abstractLockOperation { queue.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return abstractLockOperation { queue.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        abstractLockOperation {
            queue.add(element)
            null
        }
    }

    private fun abstractLockOperation(operation: () -> E?): E? {
        val curID = index.get()
        arr[curID] = operation

        while (arr[curID] == operation) {
            if (tryLock()) {
                for (i in arr.indices) {
                    val element = arr[i]

                    if (element is Function0<*>) arr[i] = element()
                }

                unlock()
            }
        }

        return arr[curID] as E?
    }

    private fun tryLock() = if (!lock.value) lock.compareAndSet(false, update = true) else false

    private fun unlock() { lock.value = false }
}

private val threadCnt = atomic(0)
private const val THREAD_NUMBER = 3