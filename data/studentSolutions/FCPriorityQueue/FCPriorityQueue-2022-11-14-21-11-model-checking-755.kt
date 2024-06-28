
import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
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
        val lock = Lock()
        lock.lock()
        val result = q.poll()
        lock.unlock()
        return result
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val lock = Lock()
                lock.lock()
         val  result =  q.peek();
        lock.unlock()
        return result
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val lock = Lock()
                lock.lock()
            q.add(element);
        lock.unlock()
    }
}

class Lock {
    private val locked = atomic<Int>(0)

    fun lock() {
        while (!locked.compareAndSet(0, 1))
        {}
    }

    fun unlock() {
        locked.value = 0
    }
}