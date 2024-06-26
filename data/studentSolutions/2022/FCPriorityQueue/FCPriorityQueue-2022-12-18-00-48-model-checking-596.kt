import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*

data class Operation(var op: String, var element: Any? = null)

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val workerNum = 4 * Runtime.getRuntime().availableProcessors() // ?
    private val ops = atomicArrayOfNulls<Operation?>(workerNum)
    private val lock = ReentrantLock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val op = Operation("poll")
        val i = doSmth(op)
        val res = op.element as E?
        ops[i].value = null
        return res
//        return q.poll()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val op = Operation("peek")
        val i = doSmth(op)
        val res = op.element as E?
        ops[i].value = null
        return res
//        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val op = Operation("add", element)
        val i = doSmth(op)
        ops[i].value = null
        q.add(element)
    }

    private val rand = Random()
    private fun doSmth(op: Operation): Int {
        while (true) {
            val ind = rand.nextInt(workerNum)
            if (ops[ind].compareAndSet(null, op)) {
//                if (operations[i].value != op) throw IllegalArgumentException("Wrong argument")
                while (true) {
                    if (lock.tryLock()) {
                        for (i in 0 until workerNum) {
                            val curOp = ops[i].value ?: continue
                            when (curOp.op) {
                                "add" -> q.add(curOp.element as E)
                                "poll" -> curOp.element = q.poll()
                                "peek" -> curOp.element = q.peek()
                                "finished" -> {}
                            }
                            curOp.op = "finished"
                        }
                        lock.unlock()
                    }
                    if (op.op == "finished") {
                        return ind
                    }
                }
            }
        }
    }
}