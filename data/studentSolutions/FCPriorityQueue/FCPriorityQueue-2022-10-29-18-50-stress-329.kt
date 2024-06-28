import java.util.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val locked = atomic(false)
    private val queries = atomicArrayOfNulls<Query<E>>(QUERIES_ARRAY_SIZE)
    private val generator = Random()

    enum class QueryType {
        POLL, PEEK, ADD, DONE
    }

    class Query<E>(private val type: QueryType, private var element: E? = null) {
        fun isExecuted() = type == QueryType.DONE

        fun executeOn(queue: PriorityQueue<E>) {
            when (type) {
                QueryType.POLL -> element = queue.poll()
                QueryType.PEEK -> element = queue.peek()
                QueryType.ADD -> queue.add(element)
                QueryType.DONE -> {}
            }
        }

        fun getElement(): E? {
            return element
        }
    }

    private fun tryLock(): Boolean {
        val expect = false
        val update = true
        return locked.compareAndSet(expect, update)
    }

    private fun unlock() {
        locked.getAndSet(false)
    }

    private fun executeQueries() {
        for (i in 0 until queries.size) {
            val query = queries[i].value ?: continue
            query.executeOn(queue)
        }
    }

    private fun executeQuery(queryType: QueryType, element: E? = null): E? {
        val query = Query(queryType, element)
        var index: Int? = null
        while (!tryLock()) {
            if (index == null) {
                val randomIndex = generator.nextInt(QUERIES_ARRAY_SIZE)
                if (queries[randomIndex].compareAndSet(null, query)) {
                    index = randomIndex
                }
            } else {
                val maybeExecuted = queries[index].value ?: throw Exception("Cell ownership rights violated")
                if (maybeExecuted.isExecuted()) {
                    queries[index].getAndSet(null) // Releasing the cell
                    return maybeExecuted.getElement()
                }
            }
        }
        executeQueries()
        val result = if (index == null) {
            query.executeOn(queue)
            query.getElement()
        } else { // Query executed because executeQueries was called
            queries[index].value?.getElement()
        }
        unlock()
        return result
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return executeQuery(QueryType.POLL)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return executeQuery(QueryType.PEEK)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        executeQuery(QueryType.ADD, element)
    }


}

const val QUERIES_ARRAY_SIZE = 12
