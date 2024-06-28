import java.util.*
import kotlinx.atomicfu.atomic

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val threadCnt = atomic(0)
    private val THREADS = 3
    private val index = ThreadLocal.withInitial{threadCnt.getAndIncrement() % THREADS}
    private val arr = arrayOfNulls<Any>(THREADS)
    private val lock = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return safe { q.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return safe { q.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        safe {
            q.add(element)
            null
        }
    }
    private fun safe(operation : () -> E?): E? {
        arr[index.get()] = operation
        while (true) {
            if (arr[index.get()] == operation) {
                break
            }
            if (!lock.value && lock.compareAndSet(expect = false, update = true)) {
                for (i in 0..arr.size) {
                    val element = arr[i]
                    if (element is Function0<*>) {
                        arr[i] = element()
                    }
                }
                lock.value = false
            }
        }
        return arr[index.get()] as E?
    }
}