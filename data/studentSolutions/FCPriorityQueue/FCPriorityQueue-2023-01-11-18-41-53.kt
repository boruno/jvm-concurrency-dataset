import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arr = atomicArrayOfNulls<Any>(8)
    private val locked = atomic(false)

    private fun tryLock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }
    private fun unlock() {
        locked.compareAndSet(expect = true, update = false)
    }

    private fun checkArray(): Boolean {
        for(i in 0 until 8) {
            if (arr[i].value == "poll") {
                var pollResult = q.poll()
                arr[i].compareAndSet("poll", pollResult)
                continue
            }
            if (arr[i].value == "peek") {
                var peekResult = q.peek()
                arr[i].compareAndSet("peek", peekResult)
                continue
            }
            if (arr[i].value != null && arr[i].value != "peek" && arr[i].value != "poll") {
                var addElement = arr[i].value as E
                q.add(addElement)
                arr[i].compareAndSet(addElement, null);
                continue
            }
        }
        return true
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var index = ThreadLocalRandom.current().nextInt(8)
        var insert = true
        while(true)
        {
            if (tryLock() && arr[index].value == null)
            {
                arr[index].value = null
                var pollResult = q.poll()
                checkArray()
                unlock()
                return pollResult
            }
            if (insert)
            {
                if (arr[index].compareAndSet(null, "poll")) { insert = false }
                else { index = ThreadLocalRandom.current().nextInt(8) }
            }
            else
            {
                if (arr[index].value != "poll") { return arr[index].value as E? }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var index = ThreadLocalRandom.current().nextInt(8)
        var insert = true
        while (true)
        {
            if (tryLock() && arr[index].value == null)
            {
                arr[index].value = null
                val peekResult = q.peek()
                checkArray()
                unlock()
                return peekResult
            }
            if (insert)
            {
                if (arr[index].compareAndSet(null, "peek")) { insert = false }
                else { index = ThreadLocalRandom.current().nextInt(8) }
            }
            else
            {
                if (arr[index].value != "peek") { return arr[index].value as E?}
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var index = ThreadLocalRandom.current().nextInt(8)
        var insert = true
        while (true) {
            if (tryLock() && arr[index].value == null)
            {
                arr[index].value = null
                q.add(element)
                checkArray()
                unlock()
                return
            }
            if (insert)
            {
                if (arr[index].compareAndSet(null, element)) { insert = false }
                else { index = ThreadLocalRandom.current().nextInt(8) }
            }
            else
            {
                if (arr[index].value != element) { return }
            }
        }
    }
}