import kotlinx.atomicfu.*
import java.util.PriorityQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val SIZE = 100

    private val pollRequest = atomicArrayOfNulls<Int>(SIZE) // null -- empty, 1 -- waiting, 2 -- result
    private val pollResult: MutableList<E?> = MutableList(SIZE) { null }

    private val peekResult: AtomicRef<E?> = atomic(null)

    private val addRequest = atomicArrayOfNulls<E>(SIZE)
    private val addRequestStatus = atomicArrayOfNulls<Int>(SIZE) // null -- empty, 1 -- busy, 2 -- waiting, 3 -- done

    private val lock = ReentrantLock()

    private fun workForEverybody() {
//        println("start workForEverybody")
        for (i in 0 until SIZE) {
            if (pollRequest[i].value == 1) {
                pollResult[i] = q.poll()
                peekResult.value = q.peek()
//                println("POLL " + poll_result[i])
                pollRequest[i].value = 2
            }

            if (addRequestStatus[i].value == 2) {
                q.add(addRequest[i].value)
                peekResult.value = q.peek()
//                println("ADD " + add_request[i].value)
                addRequestStatus[i].value = 3
            }
        }
//        println("PEEK " + q.peek())
//        println("finish workForEverybody")
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var i = ThreadLocalRandom.current().nextInt(SIZE)
        while (!pollRequest[i].compareAndSet(null, 1)) {
            i = ThreadLocalRandom.current().nextInt(SIZE)
        }
        while (true) {
//        for (aaa in 1..100000) {
            if (lock.tryLock()) {
                try {
                    workForEverybody()
                    assert (pollRequest[i].value == 2)
                } finally {
                    lock.unlock()
                }
            }
            if (pollRequest[i].value == 2) {
                val res = pollResult[i]
                pollRequest[i].value = null
                return res
            }
        }
//        throw Exception("POLL Exception")
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
//        if (lock.tryLock()) {
//            try {
//                workForEverybody()
//            } finally {
//                lock.unlock()
//            }
//        }
        return peekResult.value
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var i = ThreadLocalRandom.current().nextInt(SIZE)
        while (!addRequestStatus[i].compareAndSet(null, 1)) {
            i = ThreadLocalRandom.current().nextInt(SIZE)
        }
        addRequest[i].value = element
        addRequestStatus[i].value = 2
        while (true) {
//        for (aaa in 1..1000) {
            if (lock.tryLock()) {
                try {
                    workForEverybody()
                    assert(addRequestStatus[i].value == 3)
                } finally {
                    lock.unlock()
                }
            }
            if (addRequestStatus[i].value == 3) {
                addRequestStatus[i].value = null
                return
            }
        }
//        throw Exception("ADD Exception")
    }
}