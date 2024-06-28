import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.withLock
import java.util.*
import java.util.concurrent.locks.ReentrantLock

private enum class OperationType {
    POLL,
    PEEK,
    ADD,
    DONE
}

private class Operation<E>(val type: OperationType, val data: E? = null)

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val fcArray = atomicArrayOfNulls<Operation<E>>(FC_ARR_LEN)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while(true) {
            if (lock.isLocked) {
                val res = askForHelpFcArr(OperationType.POLL)
                if (res != null) return res.data
            }
            lock.withLock {
                val res = q.poll()
//                  check fcArray for help operations
                helpFcArr()
                return res
            }

        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while(true) {
            if (lock.isLocked) {
                val res = askForHelpFcArr(OperationType.PEEK)
                if (res != null) return res.data
            }
            lock.withLock {
                val res = q.peek()
//                  check fcArray for help operations
                helpFcArr()
                return res
            }

        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while(true) {
            if (lock.isLocked) {
                val res = askForHelpFcArr(OperationType.ADD, element)
                if (res != null) return
            }
            lock.withLock {
                val res = q.add(element)
//                  check fcArray for help operations
                helpFcArr()
                return
            }

        }
    }

    private fun helpFcArr() {
        for (i in 0 until fcArray.size) {
            val op = fcArray[i].value
            if (op != null) {
                when (op.type) {
                    OperationType.POLL -> fcArray[i].compareAndSet(op, Operation(OperationType.DONE, q.poll()))

                    OperationType.PEEK -> fcArray[i].compareAndSet(op, Operation(OperationType.DONE, q.peek()))

                    OperationType.ADD -> {
                        q.add(op.data!!)
                        fcArray[i].compareAndSet(op, Operation(OperationType.DONE))
                    }

                    OperationType.DONE -> {
                        fcArray[i].compareAndSet(op, null)
                    }
                }
            }
        }
    }

    private fun askForHelpFcArr(opType: OperationType, data: E? = null): Operation<E>?{
//        try FC_ARR_LEN + 1 times to randomly peek a free slot in fcArray
        val newOp = Operation<E>(opType, data)
        for (i in 0 until FC_ARR_LEN + 1) {
            val index = Random(RAND_SEED).nextInt(FC_ARR_LEN)
            if (fcArray[index].compareAndSet(null, newOp)) {
            //        actively wait for the operation to be done
                while (fcArray[index].value?.type != OperationType.DONE || lock.isLocked) {}

                return if (fcArray[index].value?.type == OperationType.DONE) {
                    fcArray[index].value
                } else {
                    null
                }
            }
        }
        return null
    }
}

const val FC_ARR_LEN = 3
const val RAND_SEED = 42L