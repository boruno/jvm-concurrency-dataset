import kotlinx.atomicfu.locks.withLock
import java.util.PriorityQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val lock = ReentrantLock();
    private val q = PriorityQueue<E>();

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        lock.withLock {
            return q.poll();
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        lock.withLock {
            return q.peek();
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        lock.withLock {
            q.add(element);
        }
    }
}
