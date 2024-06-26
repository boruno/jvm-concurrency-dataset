import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    enum class QueryType {
        POLL, PEEK, ADD, DONE
    }
    class QueryData<E> {
        val type: QueryType
        val argument: E?

        constructor(type: QueryType, argument: E?) {
            this.type = type
            this.argument = argument
        }
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