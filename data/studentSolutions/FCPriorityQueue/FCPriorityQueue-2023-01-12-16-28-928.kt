import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
const val ArraySize = 6
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arr = atomicArrayOfNulls<Any>(ArraySize)
    private val locked = atomic(false)

    private fun checkLock(): Boolean {
        return !locked.value
    }
    private fun tryLock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }
    private fun unlock() {
        locked.compareAndSet(expect = true, update = false)
    }
    @Suppress("UNCHECKED_CAST")
    private fun checkArray(): Boolean {
        for(i in 0 until ArraySize) {
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
    private fun isPolled(index: Int, insert: Boolean): Boolean
    {
        if (insert) {
            return arr[index].value != "poll"
        }

        return false
    }
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST")
    fun poll(): E? {
        var index = ThreadLocalRandom.current().nextInt(ArraySize)
        var insert = false
        while(true) {
            if (checkLock()) {
                if (isPolled(index, insert)) {
                    val ret = arr[index].value as E?
                    arr[index].compareAndSet(ret, null)
                    return ret
                }
                if (tryLock()) {
                    var pollResult = q.poll()
                    arr[index].value = null
                    checkArray()
                    unlock()
                    return pollResult
                }
            }
            if (!insert) {
                if (arr[index].compareAndSet(null, "poll")) {
                    insert = true
                } else {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            } else {
                if (arr[index].value != "poll") {
                    return arr[index].value as E?
                }
            }
        }
        return 777 as E
    }

    private fun isPeeked(index: Int, insert: Boolean): Boolean
    {
        if (insert) {
            return arr[index].value != "peek"
        }

        return false
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST", "UNREACHABLE_CODE")
    fun peek(): E? {
        var index = ThreadLocalRandom.current().nextInt(ArraySize)
        var insert = false
        while (true) {
            if (checkLock()) {
                if (isPeeked(index, insert)) {
                    val ret = arr[index].value as E?
                    arr[index].compareAndSet(ret, null)
                    return ret
                }
                if (tryLock()) {
                    val peekResult = q.peek()
                    arr[index].value = null
                    checkArray()
                    unlock()
                    return peekResult
                }
            }
            if (!insert) {
                if (arr[index].compareAndSet(null, "peek")) {
                    insert = true
                } else {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            } else {
                if (arr[index].value != "peek") {
                    return arr[index].value as E?
                }
            }
        }
        return 666 as E
    }

    private fun isAdded(index: Int, insert: Boolean): Boolean
    {
        if (insert) {
            return arr[index].value == null
        }

        return false
    }


    /**
     * Adds the specified element to the queue.
     */
    @Suppress("UNCHECKED_CAST", "UNREACHABLE_CODE")
    fun add(element: E) {
        var index = ThreadLocalRandom.current().nextInt(ArraySize)
        var insert = false
        while (true) {
            if (checkLock()) {
                if (isAdded(index, insert)) {
                    return
                }
                if (tryLock()) {

                    if (arr[index].compareAndSet(element, null)) return
                    q.add(element)
                    checkArray()
                    unlock()
                    return
                }
            }
            if (!insert) {
                if (arr[index].compareAndSet(null, element)) {
                    insert = true
                } else {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            } else {
                if (arr[index].value != element) {
                    return
                }
            }
        }
    }
}