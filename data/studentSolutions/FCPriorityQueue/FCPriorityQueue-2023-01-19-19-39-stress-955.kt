import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
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
            if (!myTask.equals(ar[taskIdx].value)) {
                throw RuntimeException("hueta2")
            }
            if (myTask.status == STATUS.DONE) {

                val res = myTask.res
                ar[taskIdx].value = null
                return res
            }
        }
        var res: E? = null
        for (i in 0 until 50) {
            val task = ar[i].value ?: continue

            // if (taskIdx == i) {
            //     res = q.poll()
            //     ar[taskIdx].getAndSet(null)
            //     continue
            // }

            if (task.status == STATUS.DONE) continue

            if (task.op == OP.PEEK) {
                task.res = q.peek()
                task.status = STATUS.DONE
            } else if (task.op == OP.ADD) {
                q.add(task.input)
                task.status = STATUS.DONE
            } else if (task.op == OP.POOL) {
                task.res = q.poll()
                task.status = STATUS.DONE
            }
        }

        if (myTask.status == STATUS.DONE) {
            val r = myTask.res
            ar[taskIdx].value = null
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
            if (!myTask.equals(ar[taskIdx].value)) {
                throw RuntimeException("hueta2")
            }
            if (myTask.status == STATUS.DONE) {
                val res = myTask.res
                ar[taskIdx].value = null
                return res
            }
        }

        var res: E? = null
        for (i in 0 until 50) {
            val task = ar[i].value ?: continue

            // if (taskIdx == i) {
            //     res = q.peek()
            //     ar[taskIdx].getAndSet(null)
            //     continue
            // }

            if (task.status == STATUS.DONE) continue

            if (task.op == OP.PEEK) {
                task.res = q.peek()
                task.status = STATUS.DONE
            } else if (task.op == OP.ADD) {
                q.add(task.input)
                task.status = STATUS.DONE
            } else if (task.op == OP.POOL) {

                task.res = q.poll()
                task.status = STATUS.DONE
            }
        }

        if (myTask.status == STATUS.DONE) {
            val r = myTask.res
            ar[taskIdx].value = null
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
            if (!myTask.equals(ar[taskIdx].value)) {
                throw RuntimeException("hueta2")
            }
            if (myTask.status == STATUS.DONE) {
                ar[taskIdx].value = null
                return
            }
        }

        for (i in 0 until 50) {
            val task = ar[i].value ?: continue

            // if (taskIdx == i) {
            //     q.add(element)
            //     ar[taskIdx].getAndSet(null)
            //     continue
            // }

            if (task.status == STATUS.DONE) continue

            if (task.op == OP.PEEK) {
                task.res = q.peek()
                task.status = STATUS.DONE
            } else if (task.op == OP.ADD) {
                // println("add ${task.input}")
                q.add(task.input)
                task.status = STATUS.DONE

            } else if (task.op == OP.POOL) {
                task.res = q.poll()
                task.status = STATUS.DONE
            }
        }

        if (myTask.status == STATUS.DONE) {
            ar[taskIdx].value = null
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