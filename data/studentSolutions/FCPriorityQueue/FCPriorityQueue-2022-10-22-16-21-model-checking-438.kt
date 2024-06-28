import java.util.PriorityQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReferenceArray
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val operations = AtomicReferenceArray<Pair<Boolean, (PriorityQueue<E>) -> (E?)>?>(SIZE)
    private val lock = ReentrantLock()
    private val rand = Random(1)

    private fun run(function: (PriorityQueue<E>) -> (E?)) = run(function, null)
    private fun run(function: (PriorityQueue<E>) -> (E?), index: Int?): Pair<Boolean, E?> {
        if (lock.tryLock()) {
            try {
                if (index != null) {
                    operations.compareAndSet(index, Pair(true, function), null)
                }
                val result = function.invoke(q)
                for (i in 0 until SIZE) {
                    val (stored, func) = operations.get(i) ?: continue
                    if (stored) {
                        val res = func.invoke(q)
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

    private fun execute(function: (PriorityQueue<E>) -> E?): E? {
        while (true) {
            val (success, value) = run(function)
            if (success) {
                return value
            }

            val index = tryWriteFunction(function) ?: continue

            return executeWithStoredAtIndex(index, function)
        }
    }

    private fun executeWithStoredAtIndex(index: Int, function: (PriorityQueue<E>) -> E?): E? {
        while (true) {
            val atIndex = operations.get(index)
            if (atIndex!!.second != function) {
                operations.set(index, null)
                return atIndex.second.invoke(q)
            }

            val (success, value) = run(function, index)
            if (success) {
                return value
            }
        }
    }

    private fun tryWriteFunction(function: (PriorityQueue<E>) -> (E?)): Int? {
        val i = rand.nextInt(0, SIZE)
        val success = operations.compareAndSet(i, null, Pair(true, function))

        return if (success) i else null
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = execute {
        return@execute it.poll()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = execute {
        return@execute it.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        execute {
            it.add(element)
            return@execute null
        }
        return
    }
}

private const val SIZE = 10
