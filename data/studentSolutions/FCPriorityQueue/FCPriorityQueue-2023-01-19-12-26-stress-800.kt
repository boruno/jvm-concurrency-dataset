import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Op<E>?>(4)
    private val fcLoc = ReentrantLock()
    private val debugOutput : AtomicRef<E?> = atomic(null)
    private val random = Random()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return makeOp({ q.poll() }, null, OpType.POLL)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return makeOp({ q.peek() }, null, OpType.PEEK)
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


    private fun makeOp(function: java.util.function.Function<E?, E?>, value: E?, type: OpType): E? {
        val index = random.nextInt(FC_ARRAY_SIZE)
        val op = Op(type, value)
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
                        val resultOp = fcArray[index].getAndSet(null)
                        val result: E?
                        if (resultOp!!.type == OpType.RESULT) {
                            @Suppress("UNCHECKED_CAST")
                            result = resultOp.value.value
                            debugOutput.getAndSet(result)
                        } else {
                            result = function.apply(value)
                            debugOutput.getAndSet(result)
                        }
                        help()
                        fcLoc.unlock()
                        debugOutput.getAndSet(result)
                        return result
                    }
                    val answer = fcArray[index].value
                    if (answer!!.type == OpType.RESULT) {
                        fcArray[index].getAndSet(null)
                        @Suppress("UNCHECKED_CAST")
                        return answer.value.value
                    }
                }
            }
        }
    }

    private fun help() {
        for (i in 0 until FC_ARRAY_SIZE) {
            val event = fcArray[i].value ?: continue
            val res: E?
            if (event.type == OpType.ADD) {
                @Suppress("UNCHECKED_CAST")
                q.add(event.value.value as E)
                res = null
            } else if (event.type == OpType.PEEK) {
                res = q.peek()
            } else {
                res = q.poll()
            }
            fcArray[i].getAndSet(Op(OpType.RESULT, res))
            debugOutput.getAndSet(res)
        }
    }

    private class Op<E>(type_: OpType, value_: E?) {
        val type = type_
        val value = atomic(value_)
    }

    private enum class OpType {
        POLL, ADD, PEEK, RESULT
    }
}

const val FC_ARRAY_SIZE = 4