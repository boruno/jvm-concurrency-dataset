import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayDeque

class FCPriorityQueue<E : Comparable<E>> {
    private val line = atomicArrayOfNulls<Request<E>>(15)
    private val q = atomic(PriorityQueue<E>())
    private val lock = Lock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val req = Request<E>(ReqType.Poll)
        var index = -1
        while (true) {
            if (lock.tryLock()) {
                if (index != -1) {
                    if (line[index].value!!.op == ReqType.Response) {
                        val gotVal = line[index].value?.value
                        line[index].value = null
                        lock.unlock()
                        return gotVal;
                    }
                    line[index].value = null
                }
                help(index)
                val ans = q.value.poll()

                lock.unlock()
                return ans
            } else {
                if (index != -1) { // if request was processed
                    if (line[index].value!!.op == ReqType.Response) {
                        val found: E? = line[index].value?.value
                        line[index].value = null
                        return found
                    }
                } else {
                    index = postReqAtFreeIndexGetIndex(req) // leave request
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val req = Request<E>(ReqType.Peek)
        var index = -1
        while (true) {
            if (lock.tryLock()) {
                if (index != -1) {
                    if (line[index].value!!.op == ReqType.Response) {
                        val ans = line[index].value?.value
                        line[index].value = null
                        lock.unlock()
                        return ans
                    }
                    line[index].value = null
                }

                val ans = q.value.peek()
                help(index)
                lock.unlock()
                return ans
            } else {
                if (index != -1) { // if request was processed
                    if (line[index].value!!.op == ReqType.Response) {
                        val found: E? = line[index].value?.value
                        line[index].value = null
                        return found
                    }
                } else {
                    index = postReqAtFreeIndexGetIndex(req) // leave request
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val req = Request(ReqType.Add, element)
        var index = -1
        while (true) {
            if (lock.tryLock()) {
                if (index != -1) {
                    if (line[index].value!!.op == ReqType.Response) {
                        line[index].value = null
                        lock.unlock()
                        return
                    }
                    line[index].value = null
                }

                q.value.add(element)
                help(index)
                lock.unlock()
                return
            } else {
                if (index != -1) { // if request was processed
                    if (line[index].value!!.op == ReqType.Response) {
                        line[index].value = null
                        return
                    }
                } else {
                    index = postReqAtFreeIndexGetIndex(req) // leave request
                }
            }
        }
    }

    private fun postReqAtFreeIndexGetIndex(req: Request<E>): Int {
        for (i in 0 until line.size) {
            if (line[i].compareAndSet(null, req)) {
                return i
            }
        }
        return -1
    }

    private fun help(ignore: Int = -1) {
        for (i in 0 until line.size) {
            val req = line[i].value
            val queue = q.value
            if (req != null && i != ignore) {
                var res: E?
                when (req.op) {
                    ReqType.Add -> {
                        res = null
                        queue.add(req.value)
                    }

                    ReqType.Poll -> {
                        res = queue.poll()
                    }

                    ReqType.Peek -> {
                        res = queue.peek()
                    }

                    else -> {
                        res = null
                    }
                }
                line[i].value = Request(ReqType.Response, res)
            }
        }
    }
}

class Lock {
    private val locked = atomic(false)

    fun tryLock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }

    fun unlock() {
        locked.value = false
    }
}

class Request<E>(val op: ReqType, val value: E? = null) {

}

enum class ReqType {
    Poll, Peek, Add, Response
}