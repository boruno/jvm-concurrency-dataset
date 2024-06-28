import java.util.*
import kotlinx.atomicfu.*

class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val locked = atomic(false)

    private fun tryLock() = locked.compareAndSet(false, true)

    private fun unlock() {
        locked.value = false
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return queue.poll()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return queue.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        queue.add(element)
    }
}