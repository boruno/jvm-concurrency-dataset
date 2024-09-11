import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayDeque
import kotlin.concurrent.*

class FCPriorityQueue<E : Comparable<E>> {
    private val LINE_SIZE = 100
    private val line = atomicArrayOfNulls<Request<E>>(LINE_SIZE)
    private val q = atomic(PriorityQueue<E>())
    private val lock = Lock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val req = Request<E>(ReqType.Poll, null)
        var index = 0
        while (true) {
            if (lock.tryLock()) {
                val leftMsg = line[index].getAndSet(null)
                val ans: E? = if (leftMsg?.op != ReqType.Response) {
                    q.value.poll()
                } else {
                    leftMsg.value
                }
                help()
                lock.unlock()
                return ans
            } else {
                if (line[index].value?.op == ReqType.Response) {
                    return line[index].getAndSet(null)?.value
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
        val req = Request<E>(ReqType.Peek, null)
        var index = 0
        while (true) {
            if (lock.tryLock()) {
                val leftMsg = line[index].getAndSet(null)
                val ans: E? = if (leftMsg?.op != ReqType.Response) {
                    q.value.peek()
                } else {
                    leftMsg.value
                }
                help()
                lock.unlock()
                return ans
            } else {
                if (line[index].value?.op == ReqType.Response) {
                    return line[index].getAndSet(null)?.value
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
        val req = Request<E>(ReqType.Add, element)
        val respExample = Request<E>(ReqType.Response, null)
        var index = 0
        while (true) {
            if (lock.tryLock()) {
                val leftMsg = line[index].getAndSet(null)
                if (leftMsg?.op != ReqType.Response) { // if no msg or it's not an answer
                    q.value.add(element)
                }
                help()
                lock.unlock()
                return
            } else {
                if (line[index].compareAndSet(respExample, null)) { // if request was processed
                    return
                } else {
                    index = postReqAtFreeIndexGetIndex(req) // leave request
                }
            }
        }
    }

    private fun postReqAtFreeIndexGetIndex(req: Request<E>): Int {
        for (i in 1 until line.size) {
            if (line[i].compareAndSet(null, req)) {
                return i
            }
        }
        return LINE_SIZE
    }

    private fun help(): String {
        var s = ""
        for (i in 1 until LINE_SIZE) {
            val req = line[i].value
            if (req != null) {
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
                s += res.toString() + " at " + i.toString() + " "
            }
        }
        return s
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

class Request<E>(var op: ReqType, var value: E?) {
    fun answer(newValue: E?): Request<E> {
        op = ReqType.Response
        value = newValue
        return this
    }
}

enum class ReqType {
    Poll, Peek, Add, Response
}