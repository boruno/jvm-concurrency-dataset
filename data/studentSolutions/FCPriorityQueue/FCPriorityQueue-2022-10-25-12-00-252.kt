import java.util.*
import kotlinx.atomicfu.*
import java.util.concurrent.*


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    inner class Query<E>(val op: () -> E?) {
        var appliedOp: E? = null
        var finished = false
        fun apply() {
            appliedOp = op()
            finished = true
        }
    }

    private val locked = atomic(false)
    private val queries = atomicArrayOfNulls<Query<E>>(BUFFER_ARRAY_SIZE)

    private fun apply(op: () -> E?): E? {
        System.err.println(BUFFER_ARRAY_SIZE)
        val query = Query(op)
        var index: Int
        do {
            index = ThreadLocalRandom.current().nextInt(0, BUFFER_ARRAY_SIZE)
        } while (!queries[index].compareAndSet(null, query))
        do {
            if (locked.compareAndSet(false, true)) {
                (0 until BUFFER_ARRAY_SIZE).asSequence()
                    .mapNotNull { queries[it].value }
                    .filterNot { it.finished }
                    .forEach { it.apply() }
                locked.compareAndSet(true, false)
                break
            }
        } while (!query.finished)
        queries[index].getAndSet(null)
        return query.appliedOp
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return apply { q.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return apply { q.peek() }
    }


    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        apply {
            q.add(element)
            null
        }
    }
}

private val BUFFER_ARRAY_SIZE = Runtime.getRuntime().availableProcessors()