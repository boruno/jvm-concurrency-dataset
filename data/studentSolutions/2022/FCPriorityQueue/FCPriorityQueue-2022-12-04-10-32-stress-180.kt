import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.withLock
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock


class FCPriorityQueue<E : Comparable<E>> {

    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private  val FC_SIZE =  4 * Thread.activeCount()
    private val actions = atomicArrayOfNulls<Operation<E>>(FC_SIZE)

    private fun combine() {
//        for (ind in 0 until FC_SIZE) {
//            val status = actions[ind].value
//
//            if (status !is Completed && status != null) {
//                actions[ind].compareAndSet(status, (status as () -> Completed)())
//            }
//        }
        for (i in 0 until FC_SIZE) {
            val op = actions[i].value ?: continue
            when (op.type) {
                OperationType.ADD -> {
                    if (actions[i].compareAndSet(op, Operation(OperationType.DONE, null))) q.add(op.value)
                }

                OperationType.PEEK -> actions[i].compareAndSet(op, Operation(OperationType.DONE, q.peek()))

                OperationType.POLL -> {
                    val res = q.poll()
                    if (!actions[i].compareAndSet(op, Operation(OperationType.DONE, res)) && res != null)
                        q.add(res)
                }

                OperationType.DONE -> {}
            }
        }
    }

    private fun giveToCombiner(op: Operation<E>): E? {

        var ind = ThreadLocalRandom.current().nextInt(FC_SIZE)

        while (!actions[ind].compareAndSet(null, op)) {
            ind = (ind + 1) % FC_SIZE
        }

        while (true) {
            val status = actions[ind].value
            if (status!!.type == OperationType.DONE && actions[ind].compareAndSet(status, null)) {
                return  status.value
            }
            lock.withLock {
                combine()
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (!lock.isLocked) {
            lock.withLock {
                val result = q.poll()
                combine()
                return result
            }
        }
        return giveToCombiner(Operation(OperationType.POLL, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (!lock.isLocked) {
            lock.withLock {
                val result = q.peek()
                combine()
                return result
            }
        }
        return giveToCombiner(Operation(OperationType.PEEK, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (!lock.isLocked) {
            lock.withLock {
                val result = q.add(element)
                combine()
                return
            }
        }
        giveToCombiner(Operation(OperationType.ADD, null))
    }

    private enum class OperationType {
        ADD, PEEK, POLL, DONE
    }

    private class Operation<E>(val type: OperationType, val value: E?)
}


