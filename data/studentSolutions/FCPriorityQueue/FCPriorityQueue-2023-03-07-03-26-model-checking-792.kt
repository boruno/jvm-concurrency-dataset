import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock(false)
    private val arraySize = 100
    private val array = atomicArrayOfNulls<Pair<String, E?>?>(arraySize)

    private fun checkWaiters() {
        for (i in 0 until arraySize) {
            val cur = array[i].value ?: continue
            if (cur.first == "poll")
                array[i].compareAndSet(cur, Pair("done", q.poll()))
            if (cur.first == "peek")
                array[i].compareAndSet(cur, Pair("done", q.peek()))
            if (cur.first == "add") {
                array[i].compareAndSet(cur, Pair("done", null))
                q.add(cur.second)
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var idx: Int
        while (true) {
            idx = ThreadLocalRandom.current().nextInt(arraySize)
            if (array[idx].compareAndSet(null, Pair("poll", null)))
                break
        }
        while (true) {
            if (lock.tryLock()) {
                val ans = if (array[idx].value?.first == "done") array[idx].value?.second else q.poll()
                array[idx].getAndSet(null)
                checkWaiters()
                lock.unlock()
                return ans
            }
            val res = array[idx].value
            if (res?.first == "done") {
                array[idx].compareAndSet(res, null)
                return res.second
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var idx: Int
        while (true) {
            idx = ThreadLocalRandom.current().nextInt(arraySize)
            if (array[idx].compareAndSet(null, Pair("peek", null)))
                break
        }
        while (true) {
            if (lock.tryLock()) {
                val ans = if (array[idx].value?.first == "done") array[idx].value?.second else q.peek()
                array[idx].getAndSet(null)
                checkWaiters()
                lock.unlock()
                return ans
            }
            val res = array[idx].value
            if (res?.first == "done") {
                array[idx].compareAndSet(res, null)
                return res.second
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var idx: Int
        while (true) {
            idx = ThreadLocalRandom.current().nextInt(arraySize)
            if (array[idx].compareAndSet(null, Pair("add", element)))
                break
        }
        while (true) {
            if (lock.tryLock()) {
                if (array[idx].getAndSet(null)?.first != "done")
                    q.add(element)
                checkWaiters()
                lock.unlock()
                return
            }
            val res = array[idx].value
            if (res?.first == "done") {
                array[idx].compareAndSet(res, null)
                return
            }
        }
    }
}