import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val operations = AtomicReferenceArray<(() -> (E?))?>(SIZE)
    private val lock = ReentrantLock()


    private fun run(function: () -> (E?)): Pair<Boolean, E?> {
        if (lock.tryLock()) {
            val result = function.invoke()
            for (i in 0 until SIZE) {
                val func = operations[i]?:continue
                val res = func.invoke()
                operations.set(i) { res }
            }
            lock.unlock()
            return Pair(true, result)
        }
        return Pair(false, null)
    }

    private fun execute(function: () -> E?): E? {
        while (true) {
            val (success, value) = run(function)
            if (success) {
                return value
            }

            val index = tryWriteFunction(function)?:continue

            val answer = executeWithStoredAtIndex(index, function)
            operations.set(index, null)
            return answer
        }
    }

    private fun executeWithStoredAtIndex(index: Int, function: () -> E?): E? {
        while (true) {
            val atIndex = operations[index]
            if (atIndex != function) {
                return atIndex!!.invoke()
            }

            val (success, value) = run(function)
            if (success) {
                return value
            }
        }
    }

    private fun tryWriteFunction(function: () -> (E?)) : Int? {
        val i = ThreadLocalRandom.current().nextInt(0, SIZE)
        val success = operations.compareAndSet(i, null, function)

        return if (success) i else null
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = execute { q.poll() }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = execute { q.peek() }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        execute {
            q.add(element)
            return@execute null
        }
    }
}

private const val SIZE = 10