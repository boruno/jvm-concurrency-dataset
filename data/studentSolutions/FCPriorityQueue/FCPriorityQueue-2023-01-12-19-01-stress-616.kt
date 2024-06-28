import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
const val ArraySize = 6

enum class Operation {
    poll,
    peek,
    add,
    none
}

class InsertValue {
    private var _value: Int? = null
    private var _completed = false
    private var _operation = Operation.none
    constructor(value: Int?, operation: Operation) {
        _value = value
        _operation = operation
    }
    fun getCurrentValue(): Int? {
        return _value
    }
    fun getStatus(): Boolean {
        return _completed
    }
    fun getOperation(): Operation {
        return _operation
    }
    fun setStatus(status: Boolean) {
        _completed = status
    }
    fun setCurrentValue(value: Int?) {
        _value = value
    }


}

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arr = atomicArrayOfNulls<Any>(ArraySize)
    private val locked = atomic(false)
    private var lastLock = "NULL"
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
            if (arr[i].value == null) continue
            val value = arr[i].value as InsertValue

            if (value.getOperation() == Operation.poll) {
                val result = q.poll()
                if (result == null) arr[i].value = null
                value.setCurrentValue(q.poll() as Int?)
                value.setStatus(true)
                arr[i].value = value
                continue
            }
            if (value.getOperation() == Operation.peek) {
                val result = q.poll()
                if (result == null) arr[i].value = null
                value.setCurrentValue(q.peek() as Int?)
                value.setStatus(true)
                arr[i].value = value
                continue
            }
            if (value.getOperation() == Operation.add) {
                q.add(arr[i].value as E)
                arr[i].value = null
                continue
            }
        }
        return true
    }
    private fun isPolled(index: Int, insert: Boolean): Boolean
    {
        if (insert) {
            val ret = arr[index].value as InsertValue
            if (arr[index].value == null) return true
            else if (ret.getOperation() == Operation.poll && ret.getStatus()) return true
        }
        return false
    }
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    @Suppress("UNCHECKED_CAST", "UNREACHABLE_CODE")
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
                    lastLock = "POLL"
                    var pollResult = q.poll()
                    arr[index].value = null
                    checkArray()
                    unlock()
                    return pollResult
                }
            }
            if (!insert) {
                val iV = InsertValue(null, Operation.poll)
                if (arr[index].compareAndSet(null, iV)) {
                    insert = true
                } else {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            } else {
                if (arr[index].value == null)
                    return null
                val ret = arr[index].value as InsertValue
                if (ret.getOperation() == Operation.poll && ret.getStatus()) {
                    return ret.getCurrentValue() as E?
                }
            }
        }
        return 777 as E
    }

    private fun isPeeked(index: Int, insert: Boolean): Boolean
    {
        if (insert) {
            val ret = arr[index].value as InsertValue
            if (arr[index].value == null) return true
            else if (ret.getOperation() == Operation.peek && ret.getStatus()) return true
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
                    lastLock = "PEEK"
                    val peekResult = q.peek()
                    arr[index].value = null
                    checkArray()
                    unlock()
                    return peekResult
                }
            }
            if (!insert) {
                val iV = InsertValue(null, Operation.peek)
                if (arr[index].compareAndSet(null, iV)) {
                    insert = true
                } else {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            } else {
                if (arr[index].value == null)
                    return null
                val ret = arr[index].value as InsertValue
                if (ret.getOperation() == Operation.peek && ret.getStatus()) {
                    return ret.getCurrentValue() as E?
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
                    lastLock = "ADD"
                    if (arr[index].compareAndSet(element, null)) {
                        unlock()
                        return
                    }
                    q.add(element)
                    checkArray()
                    unlock()
                    return
                }
            }
            if (!insert) {
                val iV = InsertValue(element as Int?, Operation.add)
                if (arr[index].compareAndSet(null, iV)) {
                    insert = true
                } else {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            } else {
                if (arr[index].value == null)
                    return
            }
        }
    }
}