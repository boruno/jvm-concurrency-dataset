import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Op<E>?>(4)
    private val fcLoc = ReentrantLock()
    private val random = Random()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        @Suppress("UNCHECKED_CAST")
        return makeOp({ _ -> q.poll() }, null, OpType.POLL) as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        @Suppress("UNCHECKED_CAST")
        return makeOp({ _ -> q.peek() }, null, OpType.PEEK) as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        makeOp({ x ->
            q.add(x)
            null
        }, element, OpType.ADD)
    }


    private fun makeOp(function: java.util.function.Function<E?, E?>, value: E?, type: OpType): Any? {
        val index = random.nextInt(FC_ARRAY_SIZE)
        val op = Op<E>(type, value)
        while (true) {
            if (fcLoc.tryLock()) {
                val result = function.apply(value)
                help()
                fcLoc.unlock()
                return result
            }
            if (fcArray[index].compareAndSet(null, op)) {
                while (true) {
                    if (fcLoc.tryLock()) {
                        fcArray[index].getAndSet(null)
                        val result = function.apply(value)
                        help()
                        fcLoc.unlock()
                        return result
                    }
                    val answer = fcArray[index].value
                    if (answer!!.type == OpType.RESULT) {
                        fcArray[index].getAndSet(null)
                        return answer.value
                    }
                }
            }
        }
    }

    private fun help() {
        for (i in 0 until FC_ARRAY_SIZE) {
            val event = fcArray[i].value ?: continue
            var res: E? = null
            if (event.type == OpType.ADD) {
                @Suppress("UNCHECKED_CAST")
                q.add(event.value as E)
                continue
            } else if (event.type == OpType.PEEK) {
                res = q.peek()
            } else if (event.type == OpType.POLL) {
                res = q.poll()
            }
            fcArray[i].getAndSet(Op(OpType.RESULT, res))
        }
    }

    private class Op<E>(type_: OpType, value_: Any?) {
        val type = type_
        val value = value_
    }

    private enum class OpType {
        POLL, ADD, PEEK, RESULT
    }
}

const val FC_ARRAY_SIZE = 4