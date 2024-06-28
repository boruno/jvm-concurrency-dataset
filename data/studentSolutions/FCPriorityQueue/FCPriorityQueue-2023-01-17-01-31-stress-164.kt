import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()


    class Worker<E>(val op: () -> E?) {
        var appliedOp: E? = null
        var finished = false
        fun apply() {
            finished = true
            appliedOp = op()
        }
    }

    private val threads = Runtime.getRuntime().availableProcessors()
    private val workers = atomicArrayOfNulls<Worker<E>>(threads)
    private val locked = atomic(false)


    private fun apply(op: () -> E?): E? {
        val worker = Worker(op)
        var index = ThreadLocalRandom.current().nextInt(threads)
        while (!workers[index].compareAndSet(null, worker)) {
            index = ThreadLocalRandom.current().nextInt(threads)
        }
        while (!worker.finished) {
            if (locked.compareAndSet(false, true)) {
                for (thread in 0..threads - 1) {
                    val combiner = workers[thread].value
                    if (combiner != null && !combiner.finished) {
                        combiner.apply()
                    }
                }
                locked.compareAndSet(true, false)
                break
            }
        }
        workers[index].getAndSet(null)
        return worker.appliedOp
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return apply { queue.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return apply { queue.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        apply { queue.add(element); null }
    }
}