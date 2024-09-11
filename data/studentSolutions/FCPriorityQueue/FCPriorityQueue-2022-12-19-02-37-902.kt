import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("UNCHECKED_CAST")
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val fc_array = atomicArrayOfNulls<Any>(FC_ARRAY_SIZE)

    var locked = AtomicBoolean()

    fun tryLock() = locked.compareAndSet(false, true)

    fun unlock() {
        locked.set(false)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            if (tryLock()) {
                val res = q.poll()
                unlock()
                return res
            } else {
                val idx = Random(0).nextInt(FC_ARRAY_SIZE)
                fc_array[idx].compareAndSet(null, Optional.empty<E>())
                for (i in FC_ARRAY_SIZE downTo 0) {
                    val res = fc_array[i].value
                    if (res != null && fc_array[i].compareAndSet(res, null)) {
                        return (res as Optional<*>?)?.orElse(null) as E?
                    }
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            if (tryLock()) {
                val res = q.peek()
                unlock()
                return res
            } else {
                val idx = Random(0).nextInt(FC_ARRAY_SIZE)
                fc_array[idx].compareAndSet(null, Optional.empty<E>())
                for (i in FC_ARRAY_SIZE downTo 0) {
                    val res = fc_array[i].value
                    if (res != null) {
                        return (res as Optional<*>?)?.orElse(null) as E?
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            if (tryLock()) {
                q.add(element)
                unlock()
                return
            } else {
                for (i in 0 until FC_ARRAY_SIZE) {
                    if (fc_array[i].compareAndSet(null, element)
                        && !fc_array[i].compareAndSet(Optional.of(element), null)) {
                        break
                    }
                }
            }
        }
    }
}

const val FC_ARRAY_SIZE = 6