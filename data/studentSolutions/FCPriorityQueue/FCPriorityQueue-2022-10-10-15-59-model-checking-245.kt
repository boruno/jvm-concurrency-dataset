import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return synchronized(q) {
            q.poll()
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return synchronized(q) {
            q.peek()
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        synchronized(q) {
            q.add(element)
        }
    }
}