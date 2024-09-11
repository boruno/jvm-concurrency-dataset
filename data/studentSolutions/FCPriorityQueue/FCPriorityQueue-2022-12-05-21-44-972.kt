import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {

    private val q = PriorityQueue<E>()
    private val lock = FCLock()

    /**
     * null - no threads waiting for the operation associated with this cell
     * false - some thread is waiting for the permission for the execution
     * true - combiner granted the permission for the execution
     */
    private val fcArray = atomicArrayOfNulls<Boolean?>(FC_ARRAY_SIZE)
    private val index = atomic(0)

    private fun help() {
        while(index.value < FC_ARRAY_SIZE) {
            fcArray[index.value].compareAndSet(expect = false, update = true)
            index.getAndIncrement()
        }
    }

    private fun actionOnLock(opType: Int, element: E? = null): E? {
        val value = applyOp(opType, element)
        help()
        index.getAndSet(0)
        lock.unlock()
        return value
    }

    private fun applyOp(opType: Int, element: E? = null): E? {
        var value: E? = null
        when(opType) {
            OP_POLL -> value = q.poll()
            OP_PEEK -> value = q.peek()
            OP_ADD -> q.add(element)
        }
        return value
    }

    private fun action(opType: Int, element: E? = null): E? {
        while(true) {
            if(lock.tryLock())
                return actionOnLock(opType, element)

            val i = ThreadLocalRandom.current().nextInt(FC_ARRAY_SIZE)
            if(fcArray[i].compareAndSet(expect = null, update = false)) {
                while(true) {
                    // waiting for changes by current combiner
                    if(fcArray[i].compareAndSet(expect = true, update = null))
                        return applyOp(opType, element)

                    // if the combiner missed an occupied fcArray cell, trying to lock
                    if(lock.tryLock()) {
                        fcArray[i].compareAndSet(expect = false, update = null)
                        return actionOnLock(opType, element)
                    }
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return action(OP_POLL)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return action(OP_PEEK)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        action(OP_ADD, element)
        return
    }
}

private class FCLock {
    val locked = atomic(false)

    fun tryLock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }

    fun unlock() {
        locked.getAndSet(value = false)
    }
}

private const val FC_ARRAY_SIZE = 15
private const val OP_POLL = 1
private const val OP_PEEK = 2
private const val OP_ADD = 3