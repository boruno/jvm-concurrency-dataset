import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val fcArray = FCArray<E>()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return processOp(Operation(Op.POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return processOp(Operation(Op.PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        processOp(Operation(Op.ADD, element))
    }

    private fun processOp(op: Operation<E>): E? {
        fcArray.addOp(op)
        while (true) {
            if (fcArray.trylock()) {
                for (i in 0 until ARRAY_SIZE) {
                    val curOp = fcArray.operations[i].getAndSet(null) ?: continue
                    when (curOp.type) {
                        Op.ADD -> q.add(curOp.valueOrResult)
                        Op.POLL -> curOp.valueOrResult = q.poll()
                        Op.PEEK -> curOp.valueOrResult = q.peek()
                    }
                    curOp.isDone = true
                }
                fcArray.unlock()
                return op.valueOrResult
            } else {
                if (op.isDone) {
                    return op.valueOrResult
                }
            }
        }
    }

    enum class Op {
        ADD, POLL, PEEK
    }

    class Operation<E>(val type: Op, var valueOrResult: E? = null) {
        var isDone = false
    }

    class FCArray<E> {
        val operations = atomicArrayOfNulls<Operation<E>>(ARRAY_SIZE)

        fun addOp(op: Operation<E>) {
            while (true) {
                val index = Random().nextInt().mod(ARRAY_SIZE)
                if (operations[index].compareAndSet(null, op)) return
            }
        }

        private val locked = atomic(false)

        fun trylock(): Boolean {
            return locked.compareAndSet(expect = false, update = true)
        }

        fun unlock() {
            locked.value = false
        }
    }
}

private const val ARRAY_SIZE = 50