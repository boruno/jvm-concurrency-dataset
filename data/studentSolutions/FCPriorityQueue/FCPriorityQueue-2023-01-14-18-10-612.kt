import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val size = 20 * Runtime.getRuntime().availableProcessors()
    private val array = atomicArrayOfNulls<Pair<String, E?>>(size)
    private val lock = atomic<Boolean>(false)
    private val q = PriorityQueue<E>()


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return addOp(Pair("poll", null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        addOp(Pair("add", element))
    }

    private fun addOp(op: Pair<String, E?>): E? {
        var pos = -1
        while (true) {
            if (pos == -1) {
                --pos
                while(pos < size - 1) {
                    pos += 1
                    if (array[pos].compareAndSet(null, op)) break
                }
                if (pos == size - 1) pos = -1
            } else {
                if (!lock.value && lock.compareAndSet(false, true)) {
                    for (i in 0 until size) {

                        val operation = array[i]
                        var real = operation.value ?: continue

                        if (real.first == "add" || real.first == "poll") {
                            if (array[i].compareAndSet(real, Pair("started", real.second))) {

                                if (real.first == "poll") real = Pair("poll", q.poll())
                                else q.add(real.second)

                                array[i].getAndSet(Pair("finished", real.second))
                            }
                        }
                    }
                    lock.getAndSet(false)
                }
                val result = array[pos].value!!
                if (result.first == "finished") {
                    array[pos].compareAndSet(result, null)
                    return result.second
                }
            }
        }
    }
}