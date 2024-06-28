import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val operations = atomicArrayOfNulls<Operation<Any>>(5)

    private val locked = atomic(false)
    fun tryLock() = locked.compareAndSet(false, true)
    fun unLock() {
        locked.compareAndSet(true, false)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val op = Operation<Any>({ q.poll() },OperationType.POLL)
        var i = 0
        while (true) {
            i = ThreadLocalRandom.current().nextInt(operations.size)
            if (operations[i].compareAndSet(null, op)) {
                break
            }
        }
        while (true) {
            if (tryLock()) {
                combinerRoutine()
            }
            if (operations[i].value!!.finished) {
                val res = operations[i].value!!.res
                while (true) {
                    if (operations[i].compareAndSet(op, null)) {
                        break
                    }
                }
                return res as E?
            }
        }
    }

    private fun combinerRoutine() {
        try {
            for (i in (0 until operations.size)) {
                val op = operations[i].value
                if (op != null) {
                    if(!op.finished) {
                        if (op!!.type == OperationType.POLL || op.type == OperationType.PEEK) {
                            if (q.isEmpty()) {
                                op.res = null
                                op.finished = true
                                continue
                            }
                        }
                        val res = op!!.operation.invoke()
                        op.res = res
                        op.finished = true
                    }
                }
            }
        }
        finally {
            unLock()
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val op = Operation<Any>({ q.peek() },OperationType.PEEK)
        var i = 0
        while (true) {
            i = ThreadLocalRandom.current().nextInt(operations.size)
            if (operations[i].compareAndSet(null, op)) {
                break
            }
        }
        while (true) {
            if (tryLock()) {
                combinerRoutine()
            }
            if (operations[i].value!!.finished) {
                val res = operations[i].value!!.res
                while (true) {
                    if (operations[i].compareAndSet(op, null)) {
                        break
                    }
                }
                return res as E?
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val op = Operation<Any>({ q.add(element) },OperationType.ADD)
        var i = 0
        while (true) {
            i = ThreadLocalRandom.current().nextInt(operations.size)
            if (operations[i].compareAndSet(null, op)) {
                break
            }
        }
        while (true) {
            if (tryLock()) {
                combinerRoutine()
            }
            if (operations[i].value!!.finished) {
                val res = operations[i].value!!.res
                while (true) {
                    if (operations[i].compareAndSet(op, null)) {
                        break
                    }
                }
                return
            }
        }
    }

    private class Operation<E>(val operation: () -> E?,val type :OperationType ) {
        var finished: Boolean = false
        var res: E? = null
        var seen :Boolean = false
    }
    private enum class OperationType{
        POLL,PEEK,ADD
    }
}