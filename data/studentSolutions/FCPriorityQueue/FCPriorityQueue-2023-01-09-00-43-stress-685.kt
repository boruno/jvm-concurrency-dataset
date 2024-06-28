import kotlinx.atomicfu.*
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val SIZE = Runtime.getRuntime().availableProcessors() * 2

    private val poll_request = atomicArrayOfNulls<Int>(SIZE) // null -- empty, 1 -- waiting, 2 -- result
    private val poll_result: MutableList<E?> = MutableList(SIZE) { null }

    private val peek_result: AtomicRef<E?> = atomic(null)

    private val add_request = atomicArrayOfNulls<E>(SIZE)

    private val lock = ReentrantLock()

    private fun workForEverybody() {

        for (i in 1..SIZE) {
            if (poll_request[i].equals(1)) {
                poll_result[i] = q.poll()
                poll_request[i].value = 2
            }

            if (add_request[i].value != null) {
                q.add(add_request[i].value)
                peek_result.value = q.peek()
                add_request[i].value = null
            }
        }
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var i = ThreadLocalRandom.current().nextInt(SIZE)
        while (!poll_request[i].compareAndSet(null, 1)) {
            i = ThreadLocalRandom.current().nextInt(SIZE)
        }
        while (true) {
            if (lock.tryLock()) {
                try {
                    workForEverybody()
                } finally {
                    lock.unlock()
                }
            }
            if (poll_request[i].value == 2) {
                val res = poll_result[i]
                poll_request[i].value = null
                return res
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            if (lock.tryLock()) {
                try {
                    peek_result.value = q.peek()
                } finally {
                    lock.unlock()
                }
            }
            return peek_result.value
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var i = ThreadLocalRandom.current().nextInt(SIZE)
        while (!add_request[i].compareAndSet(null, element)) {
            i = ThreadLocalRandom.current().nextInt(SIZE)
        }
        while (true) {
            if (lock.tryLock()) {
                try {
                    workForEverybody()
                } finally {
                    lock.unlock()
                }
            }
            if (add_request[i].value == null) {
                return
            }
        }
    }
}