import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayDeque

class FCPriorityQueue<E : Comparable<E>> {
    private val line = atomicArrayOfNulls<Request<E>>(1000)
    private val q = PriorityQueue<E>()
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
                if (index > 0) {
                    line[index].compareAndSet(req, null)
                }
                //help()
                lock.unlock()
                return q.poll()
            } else if (index > 0) { // if request was processed
                if (line[index].value != req) {
                    val found: E? = line[index].value?.value
                    line[index].compareAndSet(Request(ReqType.Response, found), null)
                    return found
                }
            } else {
                index = postReqAtFreeIndexGetIndex(req)
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
                if (index > 0) {
                    line[index].compareAndSet(req, null)
                }
                help()
                lock.unlock()
                return q.peek()
            } else if (index > 0) { // if request was processed
                if (line[index].value != req) {
                    val found: E? = line[index].value?.value
                    line[index].compareAndSet(Request(ReqType.Response, found), null)
                    return found
                }
            } else {
                index = postReqAtFreeIndexGetIndex(req)
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
                if (index > 0) {
                    line[index].compareAndSet(req, null)
                }
                help()
                lock.unlock()
                q.add(element)
                return
            } else if (index > 0) { // if request was processed
                if (line[index].value != req) {
                    return
                }
            } else {
                index = postReqAtFreeIndexGetIndex(req)
            }
        }
    }

    private fun postReqAtFreeIndexGetIndex(req: Request<E>): Int {
        for (i in 0 until line.size) {
            if (line[i].compareAndSet(null, req)) {
                return i
            }
        }
        return 0
    }

    private fun help() {
        for (i in 0 until line.size) {
            val req = line[i].value
            if (req != null) {
                var res: E?
                when (req.op) {
                    ReqType.Add -> {
                        res = null
                        q.add(req.value)
                    }

                    ReqType.Poll -> {
                        res = q.poll()
                    }

                    ReqType.Peek -> {
                        res = q.peek()
                    }

                    else -> {
                        res = null
                    }
                }
                line[i].compareAndSet(req, Request(ReqType.Response, res))
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