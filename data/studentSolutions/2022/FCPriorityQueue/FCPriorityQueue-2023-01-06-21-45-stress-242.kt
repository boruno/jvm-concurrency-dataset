import java.util.PriorityQueue
import kotlinx.atomicfu.*
import java.util.concurrent.locks.ReentrantLock
import java.util.Vector

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val array = Vector<Any>()
    var lock: ReentrantLock = ReentrantLock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
//        return q.poll()
        return null
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
//        return q.peek()
        return null
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
//        while (true) {
//            if (lock.tryLock()) {
//                q.add(element)
//            }
//            array.
//        }
//        q.add(element)
    }
}