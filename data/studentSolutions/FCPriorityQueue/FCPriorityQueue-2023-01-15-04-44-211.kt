import java.util.*
import kotlinx.atomicfu.*

class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val locked = atomic(false)

    private val ARRAY_SIZE = 8
    private val array = atomicArrayOfNulls<Node<E>?>(ARRAY_SIZE)
    // private val res_array = ArrayList<E?>(10).apply{}
    // private val res_array = arrayOfNulls<Any?>(10) as Array<E?>
    // private val res_array: Array<E?> = arrayOfNulls<E?>(10)
    // private val res_array = ArrayList<E?>(MutableList<E?>(ARRAY_SIZE) { null })
    // private val res_array = List.of(null, null, null, null, null, null, null, null, null, null)

    // private val res_array = atomicArrayOfNulls<Node<E>?>(ARRAY_SIZE)
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
            val node = array[i - 1].value
            if (node != null && !node.done) {
                node.fn()
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
        while (true) {
            // if (queue.isEmpty()) return null
            if (tryLock()) {
                val value = queue.poll()
                help()
                unlock()
                return value
            } else {
                val idx = Random().nextInt(ARRAY_SIZE)
                val elem = array[idx]
                if (!elem.compareAndSet(null, Node({
                    val node = elem.value
                    if (!node!!.done) {
                        node.value = queue.poll();
                        node.done = true
                    }
                }))) continue

                while (true) {
                    val node = array[idx].value
                    if (node!!.done) {
                        array[idx].value = null
                        return node.value
                    } else if (tryLock()) {
                        val value: E?
                        if (node.done) {
                            value = node!!.value
                        } else {
                            value = queue.poll()
                        }
                        array[idx].value = null
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
                val elem = array[idx]
                if (!elem.compareAndSet(null, Node({
                    val node = elem.value
                    queue.add(element)
                    node!!.done = true
                }))) continue

                while (true) {
                    val node = array[idx].value
                    if (node!!.done) {
                        array[idx].value = null
                        return
                    } else if (tryLock()) {
                        queue.add(element)
                        array[idx].value = null
                        help()
                        unlock()
                        return
                    }
                }
            }
        }
    }
}

private class Node<E>(val fn: () -> Unit) {
    var value: E? = null
    var done = false
}
