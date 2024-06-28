import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val FC_ARRAY_SIZE = 5
    private val fc_array = atomicArrayOfNulls<FlatCombingQueueType<E>?>(FC_ARRAY_SIZE) // from lecture

    interface FlatCombingQueueType<E>
    private class Peek<E> : FlatCombingQueueType<E>
    private class Add<E>(val element: E) : FlatCombingQueueType<E>
    private class Pop<E> : FlatCombingQueueType<E>
    private class InProgress<E> : FlatCombingQueueType<E>
    private class Ok<E>(val element: E? = null) : FlatCombingQueueType<E>

    val locked = atomic(false)

    fun tryLock() = locked.compareAndSet(expect = false, update = true)

    fun unlock() {
        locked.value = false
    }

    private fun flatHelper() {
        for (i in 0 until FC_ARRAY_SIZE) {
            val anyValue = fc_array[i].value ?: continue
            if (anyValue is Ok || anyValue is InProgress) continue

            if (fc_array[i].compareAndSet(anyValue, InProgress())) {
                when (anyValue) {
                    is Peek -> fc_array[i].compareAndSet(InProgress(), Ok(q.peek()))
                    is Pop -> {
                        val res = q.peek()
                        q.poll()
                        fc_array[i].compareAndSet(InProgress(), Ok(res))
                    }
                    is Add<E> -> {
                        q.add(anyValue.element)
                        fc_array[i].compareAndSet(InProgress(), Ok())
                    }
                    else -> continue
                }
            }
        }
    }

    private fun getFreeArrayIndex(anyAction: FlatCombingQueueType<E>): Int {
        while (true) {
            for (i in 0 until FC_ARRAY_SIZE) {
                if (fc_array[i].compareAndSet(null, anyAction)) {
                    return i
                }
            }
        }
    }

    private fun flatWaiter(anyAction: FlatCombingQueueType<E>): Pair<E?, Boolean> {
        val idx = getFreeArrayIndex(anyAction)
        while (true) {
            val currCell = fc_array[idx].value
            if (currCell is Ok<E>) {
                fc_array[idx].compareAndSet(currCell, null)
                return Pair(currCell.element, false)
            }
            if (tryLock()) {
                fc_array[idx].value = null
                return Pair(null, true)
            }
        }
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val lockFunction = {
            val res = q.poll()
            flatHelper()
            unlock()
            res
        }
        while (true) {
            return if (tryLock()) {
                //lockFunction()
                val res = q.poll()
                flatHelper()
                unlock()
                res
            } else {
                val res = flatWaiter(Pop())
                if (res.second) {
                    val res1 = q.poll()
                    flatHelper()
                    unlock()
                    res1
                }
                else res.first
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val lockFunction = {
            val res = q.peek()
            flatHelper()
            unlock()
            res
        }
        while (true) {
            return if (tryLock()) {
                val res = q.peek()
                flatHelper()
                unlock()
                res

            } else {
                val res = flatWaiter(Peek())
                if (res.second) {
                    val res = q.peek()
                    flatHelper()
                    unlock()
                    res
                }
                else res.first
            }
        }

    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val lockFunction = {
            val res = q.add(element)
            flatHelper()
            unlock()
            null
        }

        while (true) {
            if (tryLock()) {
                val res = q.add(element)
                flatHelper()
                unlock()
                return
            } else {
                val res = flatWaiter(Add(element))
                if (res.second) {
                    q.add(element)
                    flatHelper()
                    unlock()
                }
                return
            }
        }
    }
}