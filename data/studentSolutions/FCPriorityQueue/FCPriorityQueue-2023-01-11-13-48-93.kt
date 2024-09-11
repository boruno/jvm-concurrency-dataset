import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val FC_ARRAY_SIZE = 10
    private val fc_array = atomicArrayOfNulls<FlatCombingQueueType?>(FC_ARRAY_SIZE) // from lecture

    interface FlatCombingQueueType
    private class Peek : FlatCombingQueueType
    private class Add<E>(val element: E) : FlatCombingQueueType
    private class Pop : FlatCombingQueueType
    private class InProgress : FlatCombingQueueType
    private class Ok<E>(val element: E? = null) : FlatCombingQueueType

    val locked = atomic(false)

    fun tryLock() = locked.compareAndSet(expect = false, update = true)

    fun unlock() {
        locked.value = false
    }

    private fun flatHelper() {
        for (i in 0 until FC_ARRAY_SIZE) {
            val anyValue = fc_array[i].value ?: continue
            if (fc_array[i].compareAndSet(anyValue, InProgress())) {
                when (anyValue) {
                    is Peek -> fc_array[i].compareAndSet(InProgress(), Ok(q.peek()))
                    is Pop -> {
                        val res = q.poll()
                        fc_array[i].compareAndSet(InProgress(), Ok<E>(res))
                    }
                    is Add<*> -> {
                        q.add(anyValue.element as E)
                        fc_array[i].compareAndSet(InProgress(), Ok<E>())
                    }
                    else -> continue
                }
            }
        }
    }

    private fun getFreeArrayIndex(anyAction: FlatCombingQueueType): Int {
        while (true) {
            for (i in 0 until FC_ARRAY_SIZE) {
                if (fc_array[i].compareAndSet(null, anyAction)) {
                    return i
                }
            }
        }
    }

    private fun flatWaiter(anyAction: FlatCombingQueueType): E? {
        val idx = getFreeArrayIndex(anyAction)
        while (true) {
            val currCell = fc_array[idx].value
            if (currCell is Ok<*>) {
                if (fc_array[idx].compareAndSet(currCell, null)) return currCell.element as E?
            }
        }
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            return if (tryLock()) {
                val res = q.poll()
                flatHelper()
                unlock()
                res
            } else {
                flatWaiter(Pop())
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            return if (tryLock()) {
                val res = q.peek()
                flatHelper()
                unlock()
                res
            } else {
                flatWaiter(Peek())
            }
        }

    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            if (tryLock()) {
                val res = q.add(element)
                flatHelper()
                unlock()
                return
            } else {
                flatWaiter(Add(element))
                return
            }
        }
    }
}