import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.concurrent.thread

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
        val op = Operation<Any>({ q.poll() })
        val a = proccessOperation(op)
        return a
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val op = Operation<Any>({ q.peek() })
        return proccessOperation(op)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val op = Operation<Any>({ q.add(element) })
        proccessOperation(op)
    }

    private fun combinerRoutine() {
        try {
            for (i in (0 until operations.size)) {
                val op = operations[i].value
                if (op != null) {
                    if (!op.finished) {
                        val res = op.operation.invoke()
                        op.res = res
                        op.finished = true
                    }
                }
            }
        } finally {
            unLock()
        }
    }

    private fun proccessOperation(op: Operation<Any>): E? {
        var i = 0
        while (true) {
            i = ThreadLocalRandom.current().nextInt(operations.size)
            if (operations[i].compareAndSet(null, op)) {
                break
            }
        }
        while (true) {
            if (operations[i].value!!.finished) {
                val res = operations[i].value!!.res
                while (true) {
                    if (operations[i].compareAndSet(op, null)) {
                        break
                    }
                }
                return res as E?
            }
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

    private class Operation<E>(val operation: () -> E?) {
        var finished: Boolean = false
        var res: E? = null
        var seen: Boolean = false
    }
}