import java.util.*
import kotlinx.atomicfu.*

class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val locked = atomic(false)

    private val ARRAY_SIZE = 8
    private val fun_array = atomicArrayOfNulls<() -> Unit>(ARRAY_SIZE)
    // private val res_array = ArrayList<E?>(10).apply{}
    // private val res_array = arrayOfNulls<Any?>(10) as Array<E?>
    // private val res_array: Array<E?> = arrayOfNulls<E?>(10)
    // private val res_array = ArrayList<E?>(MutableList<E?>(ARRAY_SIZE) { null })
    // private val res_array = List.of(null, null, null, null, null, null, null, null, null, null)

    private val res_array = atomicArrayOfNulls<E?>(ARRAY_SIZE)
    // private val res_array = Collections.synchronizedList(ArrayList<E?>(8))
    // init {
    //     res_array.add(null)
    //     res_array.add(null)
    //     res_array.add(null)
    //     res_array.add(null)
    //     res_array.add(null)
    //     res_array.add(null)
    //     res_array.add(null)
    //     res_array.add(null)
    // }

    private fun tryLock() = locked.compareAndSet(false, true)

    private fun unlock() {
        locked.value = false
    }

    private fun help() {
        for (i in 1..ARRAY_SIZE) {
            val fn = fun_array[i - 1].value
            if (fn != null) {
                fn()
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    // @Synchronized
    fun poll(): E? {
        if (queue.isEmpty()) return null
        while (true) {
            if (tryLock()) {
                val value = queue.poll()
                help()
                unlock()
                return value
            } else {
                val idx = Random().nextInt(ARRAY_SIZE)
                if (!fun_array[idx].compareAndSet(null, {
                    if (res_array[idx].value == null)
                        res_array[idx].value = queue.poll()
                    }
                )) continue

                while (true) {
                    val res = res_array[idx].value
                    if (res != null) {
                        res_array[idx].value = null
                        fun_array[idx].value = null
                        return res
                    } else if (tryLock()) {
                        var value = res_array[idx].value
                        if (value != null) {
                            res_array[idx].value = null
                            fun_array[idx].value = null
                        } else {
                            fun_array[idx].value = null
                            value = queue.poll()
                        }
                        help()
                        unlock()
                        return value
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
        return queue.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    // @Synchronized
    fun add(element: E) {
        while (true) {
            if (tryLock()) {
                queue.add(element)
                help()
                unlock()
                return
            } else {
                val idx = Random().nextInt(ARRAY_SIZE)
                if (!fun_array[idx].compareAndSet(null, { queue.add(element);
                    if (res_array[idx].value == null)
                        res_array[idx].value = element;
                    }
                )) continue

                while (true) {
                    if (res_array[idx].value != null) {
                        res_array[idx].value = null
                        fun_array[idx].value = null
                        return
                    } else if (tryLock()) {
                        if (res_array[idx].value != null) {
                            res_array[idx].value = null
                            fun_array[idx].value = null
                        } else {
                            fun_array[idx].value = null
                            queue.add(element)
                        }
                        help()
                        unlock()
                        return
                    }
                }
            }
        }
    }
}
