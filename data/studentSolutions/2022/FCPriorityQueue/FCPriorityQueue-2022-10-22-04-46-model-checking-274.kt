import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = TreeSet<E>()
    private val operations = AtomicReferenceArray<Pair<Boolean, () -> (E?)>?>(SIZE)
    private val lock = ReentrantLock()


    private fun run(function: () -> (E?)) = run(function, null)
    private fun run(function: () -> (E?), index: Int?): Pair<Boolean, E?> {
        if (lock.tryLock()) {
            try {
                if (index != null) {
                    operations.set(index, null)
                }
                val result = function.invoke()
                for (i in 0 until SIZE) {
                    val (stored, func) = operations[i] ?: continue
                    if (stored) {
                        val res = func.invoke()
                        operations.set(i, Pair(false) { res })
                    }
                }
                return Pair(true, result)
            } finally {
                lock.unlock()
            }
        }
        return Pair(false, null)
    }

    private fun execute(function: () -> E?): E? {
        while (true) {
            val (success, value) = run(function)
            if (success) {
                return value
            }

            val index = tryWriteFunction(function) ?: continue

            return executeWithStoredAtIndex(index, function)
        }
    }

    private fun executeWithStoredAtIndex(index: Int, function: () -> E?): E? {
        while (true) {
            val atIndex = operations[index]
            if (atIndex != function) {
                operations.set(index, null)
                return atIndex!!.second.invoke()
            }

            val (success, value) = run(function, index)
            if (success) {
                return value
            }
        }
    }

    private fun tryWriteFunction(function: () -> (E?)) : Int? {
        val i = ThreadLocalRandom.current().nextInt(0, SIZE)
        val success = operations.compareAndSet(i, null, Pair(true, function))

        return if (success) i else null
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = execute {
        if (q.isEmpty()) {
            return@execute null
        }
        q.pollFirst()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = execute {
       if (q.isEmpty()) {
            return@execute null
       }
        q.first()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        execute {
            q.add(element)
            return@execute null
        }
        return
    }
}

private const val SIZE = 10
