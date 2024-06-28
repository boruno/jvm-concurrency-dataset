import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.PriorityQueue
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>>() {
    private val q = PriorityQueue<E>()
    private val lock: ReentrantLock = ReentrantLock()
    private val ar = atomicArrayOfNulls<Operation<E>>(50)
    // private var idx = atomic(0)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var taskIdx = -1
        val myTask: Operation<E> = Operation(OP.POOL, STATUS.WAITING, null, null)
        for (i in 0 until 50) {
            if (ar[i].compareAndSet(null, myTask)) {
                taskIdx = i
                break
            }
        }
        while (!lock.tryLock()) {
            if (ar[taskIdx].value!!.status == STATUS.DONE) {
                return ar[taskIdx].getAndSet(null)!!.res
            }
        }
        for (i in 0 until 50) {
            val task = ar[i].value ?: continue

            if (ar[i].value!!.status == STATUS.DONE) continue

            if (ar[i].value!!.op == OP.PEEK) {
                ar[i].value = (Operation(task.op, STATUS.DONE, null, q.peek()))
            } else if (task.op == OP.ADD) {
                ar[i].value=( Operation(task.op, STATUS.DONE, task.input, null))
            } else if (task.op == OP.POOL) {
                ar[i].value=( Operation(task.op, STATUS.DONE, null, q.poll()))
            }
        }

        if (ar[taskIdx].value!!.status == STATUS.DONE) {
            val r = ar[taskIdx].getAndSet(null)!!.res
            lock.unlock()
            return r
        } else {
            throw RuntimeException("hueta")
        }

        // lock.unlock()
        // return res
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var taskIdx = -1
        val myTask: Operation<E> = Operation(OP.PEEK, STATUS.WAITING, null, null)
        for (i in 0 until 50) {
            if (ar[i].compareAndSet(null, myTask)) {
                taskIdx = i
                break
            }
        }
        while (!lock.tryLock()) {
            if (ar[taskIdx].value!!.status == STATUS.DONE) {
                return ar[taskIdx].getAndSet(null)!!.res
            }
        }
        for (i in 0 until 50) {
            val task = ar[i].value ?: continue

            if (ar[i].value!!.status == STATUS.DONE) continue

            if (ar[i].value!!.op == OP.PEEK) {
                ar[i].value = (Operation(task.op, STATUS.DONE, null, q.peek()))
            } else if (task.op == OP.ADD) {
                ar[i].value=( Operation(task.op, STATUS.DONE, task.input, null))
            } else if (task.op == OP.POOL) {
                ar[i].value=( Operation(task.op, STATUS.DONE, null, q.poll()))
            }
        }

        if (ar[taskIdx].value!!.status == STATUS.DONE) {
            val r = ar[taskIdx].getAndSet(null)!!.res
            lock.unlock()
            return r
        } else {
            throw RuntimeException("hueta")
        }
        // lock.unlock()
        // return res
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var taskIdx = -1
        val myTask = Operation(OP.ADD, STATUS.WAITING, element, null)
        for (i in 0 until 50) {
            if (ar[i].compareAndSet(null, myTask)) {
                taskIdx = i
                break
            }
        }
        while (!lock.tryLock()) {
            if (ar[taskIdx].value!!.status == STATUS.DONE) {
                ar[taskIdx].getAndSet(null)
                return
            }
        }

        for (i in 0 until 50) {
            val task = ar[i].value ?: continue


            if (task.status == STATUS.DONE) continue

            if (task.op == OP.PEEK) {
                ar[i].value = (Operation(task.op, STATUS.DONE, null, q.peek()))
            } else if (task.op == OP.ADD) {
                ar[i].value=( Operation(task.op, STATUS.DONE, task.input, null))
            } else if (task.op == OP.POOL) {
                ar[i].value=( Operation(task.op, STATUS.DONE, null, q.poll()))
            }
        }

        if (ar[taskIdx].value!!.status == STATUS.DONE) {
            ar[taskIdx].getAndSet(null)
            lock.unlock()
            return
        } else {
            throw RuntimeException("hueta")
        }
    }

    class Operation<E>(val op: OP, var status: STATUS, val input: E?, var res: E?)
    enum class OP {
        ADD, PEEK, POOL
    }

    enum class STATUS {
        DONE, WAITING
    }
}