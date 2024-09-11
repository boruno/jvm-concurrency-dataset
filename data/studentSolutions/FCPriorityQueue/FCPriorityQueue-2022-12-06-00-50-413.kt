import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {

    private val q = PriorityQueue<E>()
    private val lock = FCLock()

    private val fcArray = atomicArrayOfNulls<Operation?>(FC_ARRAY_SIZE)

    private fun help() {
        for(i in 0 until FC_ARRAY_SIZE) {
            val op = fcArray[i].getAndSet(null)
            if (op != null)
                applyOp(op)
        }
    }

    private fun actionOnLock(op: Operation): E? {
        applyOp(op)
        help()
        lock.unlock()
        return op.result as E?
    }

    private fun applyOp(op: Operation) {
        when(op.type) {
            OP_POLL -> op.result = q.poll()
            OP_PEEK -> op.result = q.peek()
            OP_ADD -> q.add(op.element as E)
        }
        op.status = true
    }

    private fun waitForLock(op: Operation) {
        while(!op.status) {
            if(lock.tryLock()) {
                if(op.status) {
                    lock.unlock()
                    break
                }
                help()
                lock.unlock()
            }
        }
    }

    private fun action(opType: Int, element: E? = null): E? {
        val op = Operation(opType, element)

        if(lock.tryLock())
            return actionOnLock(op)

        while(true) {
            val index = ThreadLocalRandom.current().nextInt(FC_ARRAY_SIZE)
            if(fcArray[index].compareAndSet(expect = null, update = op))
                waitForLock(op); return op.result as E?
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
    var result: Any? = null
    var status = false
}

private const val FC_ARRAY_SIZE = 15
private const val OP_POLL = 1
private const val OP_PEEK = 2
private const val OP_ADD = 3