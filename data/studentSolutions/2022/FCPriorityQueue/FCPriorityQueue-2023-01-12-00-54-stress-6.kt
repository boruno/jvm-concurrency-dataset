import kotlinx.atomicfu.*
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReferenceArray

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fc : Array<Operation<E>>
    private val lock: AtomicBoolean = atomic(false)

    private enum class Status {
        DONE, PEEK, POLL, ENQUEUE, NONE
    }

    private class Operation<E>(status: Status = Status.NONE, obj: E? = null) {
        val obj: AtomicRef<E?> = atomic(obj)
        val status: AtomicRef<Status> = atomic(status)
    }

    init {
        val capacity = 2 * Runtime.getRuntime().availableProcessors()
        fc = Array(capacity) { _ -> Operation() }
        for (i in fc.indices) {
            fc[i] = Operation()
        }
    }

    private fun helping() {
        for (op in fc) {
            do_op(op)
        }
    }

    private fun do_op(op: Operation<E>) {
        when (op.status.value) {
            Status.NONE -> return
            Status.DONE -> return
            Status.ENQUEUE -> {
                q.add(op.obj.value!!)
                op.status.value = Status.DONE
            }
            Status.PEEK -> {
                op.obj.value = q.peek()
                op.status.value = Status.DONE
            }
            Status.POLL -> {
                op.obj.value = q.poll()
                op.status.value = Status.DONE
            }
        }
    }

    private fun help_request(op: Operation<E>): E? {
        var result: E? = null

        while (true) {
            val ind = ThreadLocalRandom.current().nextInt(q.size)

            if (fc[ind].status.compareAndSet(Status.NONE, op.status.value)) { // found cell
                fc[ind].obj.value = op.obj.value // load obj

                while (true) {
                    if (lock.compareAndSet(false, true)) { // become combiner
                        helping() // help others and yourself
                        lock.value = false // free lock
                        break
                    } else {
                        if (op.status.value == Status.DONE) {
                            break; // combiner helped us
                        }
                    }
                }

                result = op.obj.value!! // save result
                fc[ind].status.value = Status.NONE // free cell
                break
            }
        }
        return result
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return help_request(Operation(Status.POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return help_request(Operation(Status.PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        help_request(Operation(Status.ENQUEUE, element))
    }
}