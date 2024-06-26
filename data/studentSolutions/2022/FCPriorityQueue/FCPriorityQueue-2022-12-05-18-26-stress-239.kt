import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {

    private val q = PriorityQueue<E>()
    private val lock = FCLock()
    private val fcArray = atomicArrayOfNulls<Boolean?>(FC_ARRAY_SIZE)

    private fun help() {
        for(i in 0 until FC_ARRAY_SIZE)
            fcArray[i].compareAndSet(expect = false, update = true)
    }

    private fun actionOnLock(opType: Int, element: E? = null): E? {
        var value: E? = null
        when(opType) {
            OP_POLL -> value = q.poll()
            OP_PEEK -> value = q.peek()
            OP_ADD -> q.add(element)
        }
        help()
        lock.unlock()
        return value
    }

    private fun action(opType: Int, element: E? = null): E? {
        while(true) {
            var value: E? = null
            if(lock.tryLock()) {
                return actionOnLock(opType, element)
            } else {
                val index = ThreadLocalRandom.current().nextInt(FC_ARRAY_SIZE)
                if(fcArray[index].compareAndSet(null, false)) {
                    while(true) {
                        // waiting for changes by current combiner
                        if (fcArray[index].compareAndSet(expect = true, update = true)) {
                            when (opType) {
                                OP_ADD -> q.add(element)
                                OP_PEEK -> value = q.peek()
                                OP_POLL -> value = q.poll()
                            }
                            fcArray[index].compareAndSet(true, null)
                            return value
                        } else if(lock.tryLock()) {
                            fcArray[index].compareAndSet(false, null)
                            return actionOnLock(opType, element)
                        }

                        // if the combiner missed an occupied fcArray cell, trying to lock
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

private const val FC_ARRAY_SIZE = 10
private const val OP_POLL = 1
private const val OP_PEEK = 2
private const val OP_ADD = 3