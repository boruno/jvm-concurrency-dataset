import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.withLock
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock


private const val FC_SIZE = 16

class FCPriorityQueue<E : Comparable<E>> {
    private class Completed(val result: Any?)

    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val actions = atomicArrayOfNulls<Any?>(FC_SIZE)

    private fun combine() {
        for (ind in 0 until FC_SIZE) {
            val status = actions[ind].value

            if (status !is Completed && status != null) {
                actions[ind].compareAndSet(status, (status as () -> Completed)())
            }
        }
    }

    private fun <T> act(action: () -> T?): T? {

        var ind = ThreadLocalRandom.current().nextInt(FC_SIZE)

        val wrappedAction: () -> Completed = { Completed(action()) }

        while (!actions[ind].compareAndSet(null, wrappedAction)) {
            ind = (ind + 1) % FC_SIZE
        }

        while (true) {
            val status = actions[ind].value

            if (status is Completed) {
                val result = status.result
                actions[ind].compareAndSet(status, null)
                return result as T?
            }

            lock.withLock {
                combine()
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (!lock.isLocked) {
            lock.withLock {
                val result = q.poll()
                combine()
                return result
            }
        }
        return act { q.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (!lock.isLocked) {
            lock.withLock {
                val result = q.peek()
                combine()
                return result
            }
        }
        return act { q.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (!lock.isLocked) {
            lock.withLock {
                val result = q.add(element)
                combine()
                return
            }
        }
        act { q.add(element) }
    }
}


