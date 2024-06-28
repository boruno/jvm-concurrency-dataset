import java.util.*
import kotlinx.atomicfu.atomic

enum class QueryType {
    POLL, PEEK, ADD, DONE
}

class QueryData<E>(val type: QueryType, val argument: E? = null)

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val locked = atomic(false)

    fun tryLock() {
        locked.compareAndSet(false, true);
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return q.poll()
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
        q.add(element)
    }
}