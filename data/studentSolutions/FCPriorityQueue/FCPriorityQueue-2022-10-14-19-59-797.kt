import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayDeque

class FCPriorityQueue<E : Comparable<E>> {
    private val LINE_SIZE = 15
    private val line = atomicArrayOfNulls<Request<E>>(LINE_SIZE)
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
                    val gotVal = line[index].getAndSet(null)
                    if (gotVal!!.op == ReqType.Response) {
                        lock.unlock()
                        System.out.println("got from help " + gotVal.value)
                        return gotVal.value;
                    }
                }

                val ans = q.value.poll()
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
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val req = Request<E>(ReqType.Peek)
        var index = -1
        while (true) {
            if (lock.tryLock()) {
                if (index != -1) {
                    val gotVal = line[index].getAndSet(null)
                    if (gotVal!!.op == ReqType.Response) {
                        lock.unlock()
                        return gotVal.value;
                    }
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
                    val gotVal = line[index].getAndSet(null)
                    if (gotVal!!.op == ReqType.Response) {
                        lock.unlock()
                        return
                    }
                }
                help(index)
                q.value.add(element)

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
        //val l = line
        for (i in 0 until LINE_SIZE) {
            val req = line[i].value
            if (req != null && i != ignore) {
                var res: E?
                when (req.op) {
                    ReqType.Add -> {
                        res = null
                        q.value.add(req.value)
                    }

                    ReqType.Poll -> {
                        res = q.value.poll()
                    }

                    ReqType.Peek -> {
                        res = q.value.peek()
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