import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>>() {
    private val q = PriorityQueue<E>()
    private val lock: ReentrantLock = ReentrantLock()
    private val ar = atomicArrayOfNulls<Operation<E>>(50)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var taskIdx = -1
        while (!lock.tryLock()) {
            if (taskIdx == -1) {
                // val taskIdx2 = ThreadLocalRandom.current().nextInt(0, 50)
                for (i in 0 until 50) {
                    if (ar[i].compareAndSet(null, Operation(OP.POOL, STATUS.WAITING, null, null))) {
                        taskIdx = i
                        break
                    }
                }
            } else {
                if (ar[taskIdx].value?.status == STATUS.DONE) {
                    return ar[taskIdx].getAndSet(null)?.res
                }
            }
        }
        var res: E? = null
        if (taskIdx != -1) {
            val x = ar[taskIdx].value
            if (x != null) {
                if (x.status == STATUS.DONE) {
                    res = x.res
                }
            }
        }
        
        // var res: E? = null
        for (i in 0 until 50) {
            val task = ar[i].value ?: continue

            if (taskIdx == i) {
                if (task.status == STATUS.DONE) {
                    res = task.res
                }
                // ar[i].compareAndSet(task, null)
                continue
            }

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

        if (taskIdx != -1) {
            ar[taskIdx].getAndSet(null)
        }

        if (res != null) {
            lock.unlock()
            return res
        }

        res = q.poll()
        lock.unlock()
        return res
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var taskIdx = -1
        while (!lock.tryLock()) {
            if (taskIdx == -1) {
                // val taskIdx2 = ThreadLocalRandom.current().nextInt(0, 50)
                for (i in 0 until 50) {
                    if (ar[i].compareAndSet(null, Operation(OP.PEEK, STATUS.WAITING, null, null))) {
                        taskIdx = i
                    }
                    break
                }
            } else {
                if (ar[taskIdx].value?.status == STATUS.DONE) {
                    val res = ar[taskIdx].value?.res
                    ar[taskIdx].value = null
                    return res
                }
            }
        }
        var res: E? = null
        if (taskIdx != -1) {
            val x = ar[taskIdx].getAndSet(null)
            if (x!!.status == STATUS.DONE) {
                res = x.res
            }
        }


        for (i in 0 until 50) {
            val task = ar[i].value ?: continue

            if (taskIdx == i) {
                if (task.status == STATUS.DONE) {
                    res = task.res
                }
                ar[i].compareAndSet(task, null)
                // ar[i].value = null
                continue
            }


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

        if (res != null) {
            lock.unlock()
            return res
        }

        res = q.peek()
        lock.unlock()
        return res
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var taskIdx = -1
        while (!lock.tryLock()) {
            if (taskIdx == -1) {
                // val taskIdx2 = ThreadLocalRandom.current().nextInt(0, 50)
                for (i in 0 until 50) {
                    if (ar[i].compareAndSet(null, Operation(OP.ADD, STATUS.WAITING, element, null))) {
                        taskIdx = i
                        break
                    }
                }
            } else {
                if (ar[taskIdx].value?.status == STATUS.DONE) {
                    // val res = ar[taskIdx].value?.res

                    // ar[taskIdx].getAndSet(null)
                    return;
                }
            }
        }

        var ok = false
        if (taskIdx != -1) {
            val x = ar[taskIdx].getAndSet(null)
            if (x != null) {
                if (x.status == STATUS.DONE) ok = true
            }
        }


        for (i in 0 until 50) {
            val task = ar[i].value ?: continue

            if (taskIdx == i) {
                if (task.status == STATUS.DONE) {
                    ok = true
                }
                // ar[i].compareAndSet(task, null)
                continue
            }

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

        if (taskIdx != -1) {
            ar[taskIdx].getAndSet(null)
        }
        if (ok) {
            lock.unlock()
            return
        }
        q.add(element)
        lock.unlock()
    }

    class Operation<E>(val op: OP, var status: STATUS, val input: E?, var res: E?)
    enum class OP {
        ADD, PEEK, POOL
    }

    enum class STATUS {
        DONE, WAITING
    }
}