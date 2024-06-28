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
    private val fcArray = atomicArrayOfNulls<Operation?>(FC_ARRAY_SIZE)

    private fun help() {
        for(i in 0 until FC_ARRAY_SIZE) {
            val op = fcArray[i].value
            if (op != null) {
                applyOp(op)
                fcArray[i].getAndSet(null)
            }
        }
    }

    private fun actionOnLock(op: Operation): E? {
        applyOp(op)
        help()
        lock.unlock()
        return op.result.value as E
    }

    private fun applyOp(op: Operation) {
        when(op.type) {
            OP_POLL -> op.result.getAndSet(q.poll())
            OP_PEEK -> op.result.getAndSet(q.peek())
            OP_ADD -> q.add(op.element as E)
        }
        op.status.getAndSet(true)
    }

    private fun action(opType: Int, element: E? = null): E? {
        val op = Operation(opType, element)

        if(lock.tryLock())
            return actionOnLock(op)

        while(true) {
            val index = ThreadLocalRandom.current().nextInt(FC_ARRAY_SIZE)
            if (fcArray[index].compareAndSet(expect = null, update = op)) {
                while (!op.status.value) {
                    // if the combiner missed an occupied fcArray cell, trying to lock
                    if (lock.tryLock()) {
                        if(op.status.value) {
                            lock.unlock()
                            break
                        }
                        help()
                        lock.unlock()
                    }
                    return op.result.value as E?
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

private class Operation(val type: Int, val element: Any?) {
    val result = atomic<Any?>(null)
    val status = atomic<Boolean>(false)
}

private const val FC_ARRAY_SIZE = 15
private const val OP_POLL = 1
private const val OP_PEEK = 2
private const val OP_ADD = 3