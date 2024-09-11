import kotlinx.atomicfu.*
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val SIZE = Runtime.getRuntime().availableProcessors() * 2

    private val poll_request = atomicArrayOfNulls<Int>(SIZE) // null -- empty, 1 -- waiting, 2 -- result
    private val poll_result: MutableList<E?> = MutableList(SIZE) { null }

    private val peek_result: AtomicRef<E?> = atomic(null)

    private val add_request = atomicArrayOfNulls<E>(SIZE)
    private val add_request_status = atomicArrayOfNulls<Int>(SIZE) // null -- empty, 1 -- busy, 2 -- waiting, 3 -- done

    private val lock = ReentrantLock()

    private fun workForEverybody() {
//        println("start workForEverybody")
        for (i in 0 until SIZE) {
            if (poll_request[i].value == 1) {
                poll_result[i] = q.poll()
                poll_request[i].value = 2
            }

            if (add_request_status[i].value == 2) {
                q.add(add_request[i].value)
                add_request_status[i].value = 3
            }

            peek_result.value = q.peek()
        }
//        println("finish workForEverybody")
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
//        for (aaa in 1..100000) {
            if (lock.tryLock()) {
                try {
                    workForEverybody()
                    assert (poll_request[i].value == 2)
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
        throw Exception("POLL Exception")
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (lock.tryLock()) {
            try {
                println("PEEK LOCK")
                peek_result.value = q.peek()
            } finally {
                lock.unlock()
            }
        }
        return peek_result.value
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var i = ThreadLocalRandom.current().nextInt(SIZE)
        while (!add_request_status[i].compareAndSet(null, 1)) {
            i = ThreadLocalRandom.current().nextInt(SIZE)
        }
        add_request[i].value = element
        add_request_status[i].value = 2
        while (true) {
//        for (aaa in 1..1000) {
            if (lock.tryLock()) {
                try {
                    workForEverybody()
                    assert(add_request_status[i].value == 3)
                } finally {
                    lock.unlock()
                }
            }
            if (add_request_status[i].value == 3) {
                add_request_status[i].value = null
                return
            }
        }
        throw Exception("ADD Exception")
    }
}