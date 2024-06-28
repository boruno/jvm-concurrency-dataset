import kotlinx.atomicfu.atomic
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val lock = atomic(false)
    private val q = PriorityQueue<E>()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            if (lock.compareAndSet(false, true)) {
                val x = q.poll()
                lock.getAndSet(false)
                return x
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            if (lock.compareAndSet(false, true)) {
                val x = q.peek()
                lock.getAndSet(false)
                return x
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            if (lock.compareAndSet(false, true)) {
                val x = q.add(element)
                lock.getAndSet(false)
                return
            }
        }
    }
}