import java.util.*
import kotlinx.atomicfu.atomic

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private var cnt = 0
    private val index = ThreadLocal.withInitial { threadCnt.getAndIncrement() % THREADS }
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

    private fun safe(operation: () -> E?): E? {
        arr[index.get()] = operation
        while (true) {
            if (arr[index.get()] !== operation) {
                break
            }
            if (!lock.value) {
                if (lock.compareAndSet(expect = false, update = true)) {
                    for (i in arr) {
                        if (i is Function0<*>) {
                            arr[cnt] = i()
                        }
                        cnt++
                    }
                    lock.value = false
                }
            }
        }
        return arr[index.get()] as E?
    }
}
private val threadCnt = atomic(0)
private val THREADS = 20