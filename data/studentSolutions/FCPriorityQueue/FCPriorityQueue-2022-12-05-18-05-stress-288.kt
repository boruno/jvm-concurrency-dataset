import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {

    private val q = PriorityQueue<E>()
    private val lock = FCLock()
    private val fcArray = atomicArrayOfNulls<Operation>(FC_ARRAY_SIZE)

    private fun help() {
        for(i in 0 until FC_ARRAY_SIZE) {
            fcArray[i].value ?: continue
            when (fcArray[i].value!!.type) {
                OP_ADD -> q.add(fcArray[i].value!!.value.value as E)
                OP_PEEK -> fcArray[i].value!!.value.compareAndSet(null, q.peek())
                OP_POLL -> fcArray[i].value!!.value.compareAndSet(null, q.poll())
            }
            fcArray[i].value!!.fulfilled.compareAndSet(expect = false, update = true)
        }
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
            var value: E?
            if(lock.tryLock()) {
                return actionOnLock(opType, element)
            } else {
                val index = ThreadLocalRandom.current().nextInt(FC_ARRAY_SIZE)
                val op = Operation(opType, element)
                if(fcArray[index].compareAndSet(null, op)) {
                    while(true) {
                        // waiting for changes by current combiner
                        if (fcArray[index].value!!.fulfilled.compareAndSet(expect = true, update = true)) {
                            value = fcArray[index].value!!.value.value as E?
                            fcArray[index].compareAndSet(op, null)
                            return value
                        }

                        // if the combiner missed an occupied fcArray cell, trying to lock
                        if(lock.tryLock()) {
                            if (fcArray[index].value!!.fulfilled.compareAndSet(expect = true, update = true)) {
                                value = fcArray[index].value!!.value.value as E?
                                fcArray[index].compareAndSet(op, null)
                                help()
                                lock.unlock()
                                return value
                            }

                            fcArray[index].compareAndSet(op, null)

                            return actionOnLock(opType, element)
                        }
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

private class Operation(val type: Int, val element: Any?) {
    val fulfilled = atomic<Boolean>(false)
    val value = atomic(element)
}

private const val FC_ARRAY_SIZE = 10
private const val OP_POLL = 1
private const val OP_PEEK = 2
private const val OP_ADD = 3