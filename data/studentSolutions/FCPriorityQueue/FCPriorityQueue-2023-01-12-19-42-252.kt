import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.atomicfu.*
import java.beans.ExceptionListener

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    val locked = atomic(0L)
    val number = atomic(0L)
    private val arr = arrayOfNulls<Any>(ARRAY_SIZE)
    private val cmd = arrayOfNulls<Any>(ARRAY_SIZE)
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var i = number.incrementAndGet()
        cmd[ ( i % ARRAY_SIZE ).toInt() ] = 1
        while( true ) {
            if (locked.compareAndSet(0, 1)) {
                var result = q.poll()
                locked.compareAndSet( 1, 0 )
                return result
            } else {
                if ( arr[ ( i % ARRAY_SIZE ).toInt() ] != null ) {
                    var result = arr[ ( i % ARRAY_SIZE ).toInt() ]
                    arr[ ( i % ARRAY_SIZE ).toInt() ] = null
                    cmd[ ( i % ARRAY_SIZE ).toInt() ] = null
                    return result as E?
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var i = number.incrementAndGet()
        cmd[ ( i % ARRAY_SIZE ).toInt() ] = 2
        while( true ) {
            if (locked.compareAndSet(0, 1)) {
                var result = q.peek()
                locked.compareAndSet( 1, 0 )
                return result
            } else {
                if ( arr[ ( i % ARRAY_SIZE ).toInt() ] != null ) {
                    var result = arr[ ( i % ARRAY_SIZE ).toInt() ]
                    arr[ ( i % ARRAY_SIZE ).toInt() ] = null
                    cmd[ ( i % ARRAY_SIZE ).toInt() ] = null
                    return result as E?
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var i = number.incrementAndGet()
        arr[ ( i % ARRAY_SIZE ).toInt() ] = element
        while( true ) {
            if (locked.compareAndSet(0, 1)) {
                for ( i in 1..ARRAY_SIZE) {
                    if ( arr[ ( i % ARRAY_SIZE ).toInt() ] != null) {
                        q.add( arr[ ( i % ARRAY_SIZE ).toInt() ] as E )
                        arr[ ( i % ARRAY_SIZE ).toInt() ] = null
                    } else {
                        if (arr[(i % ARRAY_SIZE).toInt()] == null && cmd[(i % ARRAY_SIZE).toInt()] == 1) {
                            arr[(i % ARRAY_SIZE).toInt()] = q.poll()
                        } else {
                            if (arr[(i % ARRAY_SIZE).toInt()] == null && cmd[(i % ARRAY_SIZE).toInt()] == 2) {
                                arr[(i % ARRAY_SIZE).toInt()] = q.peek()
                            }
                        }
                    }
                }
                locked.compareAndSet( 1, 0 )
                return
            } else {
                if ( arr[ ( i % ARRAY_SIZE ).toInt() ] == null ) {
                    return
                }
            }
        }
    }
}

const val ARRAY_SIZE = 122