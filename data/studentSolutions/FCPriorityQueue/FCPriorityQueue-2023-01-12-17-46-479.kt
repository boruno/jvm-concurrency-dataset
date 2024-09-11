import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.atomicfu.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    val locked = atomic(0)
    private val arr = arrayOfNulls<Any>(ARRAY_SIZE)
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {

        while( true ) {
            if (locked.compareAndSet(0, 1)) {
                var result = q.poll()
                locked.compareAndSet( 1, 0 )
                return result
            }
        }


    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while( true ) {
            if (locked.compareAndSet(0, 1)) {
                var result = q.peek()
                locked.compareAndSet( 1, 0 )
                return result
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while( true ) {
            if (locked.compareAndSet(0, 1)) {
                q.add( element )
                locked.compareAndSet( 1, 0 )
            }
        }
    }
}

const val ARRAY_SIZE = 122