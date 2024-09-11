import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fc_array = atomicArrayOfNulls<Op<E>?>(4)
    private var fc_loc = ReentrantLock()
    private val random = Random()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return makeOp({ _ -> q.poll()}, null, OpType.POLL) as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return makeOp({_ -> q.peek()}, null, OpType.PEEK) as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        makeOp({ a ->
            q.add(a)
        }, element, OpType.ADD)
    }


    private fun makeOp(function: java.util.function.Function<E?, Any>, value : E?, type : OpType) : Any? {
        val index = random.nextInt(FC_ARRAY_SIZE)
        val op = Op<E>(type, value)
        var isLocked = false
        while (true) {
            if (fc_loc.tryLock()) {
                val result = function.apply(value)
                help()
                fc_loc.unlock()
                return result
            }
            if (fc_array[index].compareAndSet(null, op)) {
                while (true) {
                    if (fc_loc.tryLock()) {
                        fc_array[index].value = null
                        val result = function.apply(value)
                        help()
                        fc_loc.unlock()
                        return result
                    }
                    val answer = fc_array[index].value
                    if (answer!!.type == OpType.RESULT) {
                        fc_array[index].value = null
                        return answer.value
                    }
                }
            }
        }
    }

    private fun help() {
        for (i in 0 .. FC_ARRAY_SIZE) {
            val event = fc_array[i].value ?: continue
            var res : E? = null
            if (event.type == OpType.ADD) {
                q.add(event.value as E)
                continue
            } else if (event.type == OpType.PEEK) {
                res = q.peek()
            } else if (event.type == OpType.POLL) {
                res = q.poll()
            }
            fc_array[i].value = Op<E>(OpType.RESULT, res)
        }
    }

    private class Op<E>(type_ : OpType, value_ : Any?) {
        val type = type_
        val value = value_
    }

    private enum class OpType {
        POLL, ADD, PEEK, RESULT
    }
}

const val FC_ARRAY_SIZE = 4