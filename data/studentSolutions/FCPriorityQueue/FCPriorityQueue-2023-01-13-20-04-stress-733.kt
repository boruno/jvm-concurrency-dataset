import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import javax.print.attribute.standard.DateTimeAtCompleted

const val ArraySize = 6

enum class Operation {
    poll,
    peek,
    add,
    none
}

class InsertValue <E> {
    private val _value: AtomicRef<E?> = atomic(null)
    private val _completed = atomic(false)
    private val _operation = atomic(Operation.none)

    constructor(value: E?, operation: Operation, completed: Boolean) {
        _value.compareAndSet(null, value)
        _operation.compareAndSet(Operation.none, operation)
        _completed.compareAndSet(false, completed)
    }
    fun setStatus(status: Boolean) {
        val completed = _completed.value
        _completed.compareAndSet(completed, status)
    }
    fun setCurrentValue(value: E?) {
        val curValue = _value.value
        _value.compareAndSet(curValue, value)
    }
    fun getCurrentValue(): E? {
        return _value.value
    }
    fun getStatus(): Boolean {
        return _completed.value
    }
    fun getOperation(): Operation {
        return _operation.value
    }
}

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arr = atomicArrayOfNulls<Any>(ArraySize)
    private val locked = atomic(false)
    private var lastLock = "NULL"
    private fun isUnlocked(): Boolean {
        return !locked.value
    }
    private fun tryLock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }
    private fun unlock() {
        locked.compareAndSet(expect = true, update = false)
    }


    @Suppress("UNCHECKED_CAST")
    private fun checkArray() {
        for (i in 0 until ArraySize) {
            val check = arr[i].value
            if (check == null) continue // ToDo:: check this | change position
            val iV = check as InsertValue<E>
            val copyValue = arr[i].value
            if (iV.getOperation() == Operation.poll) {
                if (!iV.getStatus()) {
                    val retPoll = q.poll() // ToDo:: check deserialize value | null
                    arr[i].compareAndSet(copyValue, InsertValue<E>(retPoll, Operation.poll, true))
                    continue
                }
            }
            if (iV.getOperation() == Operation.peek) {
                if (!iV.getStatus()) {
                    val retPoll = q.peek() // ToDo:: check deserialize value | null
                    arr[i].compareAndSet(copyValue, InsertValue<E>(retPoll, Operation.peek, true))
                    continue
                }
            }
            if (iV.getOperation() == Operation.add) {
                if (!iV.getStatus()) {
                    q.add(iV.getCurrentValue())
                    arr[i].compareAndSet(copyValue, InsertValue<E>(null, Operation.add, true)) // ToDo:: check this | null return here value
                    continue
                }
            }
        }
    }
    private fun isPolled(index: Int, insert: Boolean): Boolean {
        if (insert) {
            if (arr[index].value == null) { // ToDo:: check this | cannot be null, maybe throw exp
                throw NullPointerException("Cannot be null")
            }
            val polledValue = arr[index].value as InsertValue<E>
            if (polledValue.getOperation() == Operation.poll && polledValue.getStatus()) {
                return true
            }
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
        var iV = InsertValue(null, Operation.poll, false)
        while(true) {
            if (!insert)
            {
                if (arr[index].compareAndSet(null, iV))
                {
                    insert = true
                }
                else
                {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            }
            if (isUnlocked()) {
                if (isPolled(index, insert)) {
                    val polledValue = arr[index].value
                    arr[index].compareAndSet(polledValue, null) // ToDo:: check this | null
                    return (polledValue as InsertValue<E>).getCurrentValue()
                }
                else if (tryLock()) {
                    lastLock = "POLL"
                    if (isPolled(index, insert)) {
                        val polledValue = arr[index].value
                        arr[index].compareAndSet(polledValue, null) // ToDo:: check this | null
                        unlock()
                        return (polledValue as InsertValue<E>).getCurrentValue()
                    }
                    val polledValue = q.poll()
                    if (arr[index].value != null) {
                        val copyValue = arr[index].value
                        arr[index].compareAndSet(copyValue, null) // ToDo:: check this | null
                    }
                    checkArray()
                    unlock()
                    return polledValue
                }
            } else {
                //if (arr[index].value == null) {  } // throw exp | cannot be null
                val polledValue = arr[index].value as InsertValue<E>
                if (polledValue.getOperation() == Operation.poll) {
                    if (polledValue.getStatus()) {
                        val copyValue = arr[index].value
                        arr[index].compareAndSet(copyValue, null)
                        return polledValue.getCurrentValue()
                    }
                }
            }
        }
    }

    private fun isPeeked(index: Int, insert: Boolean): Boolean {
        if (insert) {
            if (arr[index].value == null) { // ToDo:: check this | cannot be null, maybe throw exp
                throw NullPointerException("Cannot be null")
            }
            val peekedValue = arr[index].value as InsertValue<E>
            if (peekedValue.getOperation() == Operation.peek && peekedValue.getStatus()) {
                return true
            }
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
        var iV = InsertValue<E>(null, Operation.peek, false)
        while (true) {
            if (!insert)
            {
                if (arr[index].compareAndSet(null, iV))
                {
                    insert = true
                }
                else
                {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            }
            if (isUnlocked()) {
                if (isPeeked(index, insert)) {
                    val peekedValue = arr[index].value
                    arr[index].compareAndSet(peekedValue, null) // ToDo:: check this | null
                    return (peekedValue as InsertValue<E>).getCurrentValue()
                }
                else if (tryLock()) {
                    lastLock = "PEEK"
                    if (isPeeked(index, insert)) {
                        val peekedValue = arr[index].value
                        arr[index].compareAndSet(peekedValue, null) // ToDo:: check this | null
                        unlock()
                        return (peekedValue as InsertValue<E>).getCurrentValue()
                    }
                    val peekedValue = q.peek()
                    if (arr[index].value != null) {
                        val copyValue = arr[index].value
                        arr[index].compareAndSet(copyValue, null) // ToDo:: check this | null
                    }
                    checkArray()
                    unlock()
                    return peekedValue
                }
            } else {
                //if (arr[index].value == null) {  } // throw exp | cannot be null
                val peekedValue = arr[index].value as InsertValue<E>
                if (peekedValue.getOperation() == Operation.peek) {
                    if (peekedValue.getStatus()) {
                        val copyValue = arr[index].value
                        arr[index].compareAndSet(copyValue, null)
                        return peekedValue.getCurrentValue()
                    }
                }
            }
        }
    }

    private fun isAdded(index: Int, insert: Boolean): Boolean // check here
    {
        if (insert) {
            if (arr[index].value == null) { // ToDo:: check this | cannot be null, maybe throw exp
                throw NullPointerException("Cannot be null")
            }
            val addedValue = arr[index].value as InsertValue<E>
            if (addedValue.getOperation() == Operation.add && addedValue.getStatus()) {
                return true
            }
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
        var iV = InsertValue(element, Operation.add, false)
        while (true) {
            if (!insert)
            {
                if (arr[index].compareAndSet(null, iV))
                {
                    insert = true
                }
                else
                {
                    index = ThreadLocalRandom.current().nextInt(ArraySize)
                }
            }
            if (isUnlocked()) {
                if (isAdded(index, insert)) {
                    val addedValue = arr[index].value as InsertValue<E>
                    arr[index].compareAndSet(addedValue, null)
                    return
                }
                else if (tryLock()) {
                    lastLock = "ADD"
                    if (isAdded(index, insert)) { // ToDo:: check this!
                        val addedValue = arr[index].value as InsertValue<E>
                        arr[index].compareAndSet(addedValue, null)
                        unlock()
                        return
                    }
                    q.add(element)
                    if (arr[index].value != null) {
                        val addedValue = arr[index].value as InsertValue<E>
                        arr[index].compareAndSet(addedValue, null)
                    }
                    checkArray()
                    unlock()
                    return
                }
            } else {
                val addedValue = arr[index].value as InsertValue<E>
                if (addedValue.getOperation() == Operation.add && addedValue.getStatus()) {
                    arr[index].compareAndSet(addedValue, null)
                    return
                }
            }
        }
    }
}