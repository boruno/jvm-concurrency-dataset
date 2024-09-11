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
    private val q = PriorityQueue<E>()
    private val lock = Lock()
    private val helpLock = Lock()
    private val getIndexLock = Lock()
    private val lastRequestId = atomic(0L)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val id = lastRequestId.getAndIncrement()
        val req = Request<E>(ReqType.Poll, null, id)
        var index = 0
        while (true) {
            if (lock.tryLock()) {
                val leftMsg = getHelpedRequest(req.id)
                val ans: E? = if (leftMsg?.op != ReqType.Response) {
                    q.poll()
                } else {
                    leftMsg.getMessage()
                }
                help()
                lock.unlock()
                return ans
            } else {
                if (line[index].value?.op == ReqType.Response) {
                    return line[index].getAndSet(null)?.msg
                } else {
                    if (index == 0) {
                        index = postReqAtFreeIndexGetIndex(req)
                    } // leave request
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val id = lastRequestId.getAndIncrement()
        val req = Request<E>(ReqType.Peek, null, id)
        var index = 0
        while (true) {
            if (lock.tryLock()) {
                val leftMsg = getHelpedRequest(req.id)
                val ans: E? = if (leftMsg?.op != ReqType.Response) {
                    q.peek()
                } else {
                    leftMsg.msg
                }
                help()
                lock.unlock()
                return ans
            } else {
                if (line[index].value?.op == ReqType.Response) {
                    return line[index].getAndSet(null)?.msg
                } else {
                    if (index == 0) {
                        index = postReqAtFreeIndexGetIndex(req)
                    } // leave request
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val id = lastRequestId.getAndIncrement()
        val req = Request<E>(ReqType.Add, element, id)
        var index = 0
        while (true) {
            if (lock.tryLock()) {
                val leftMsg = getHelpedRequest(req.id)
                if (leftMsg?.op != ReqType.Response) { // if no msg or it's not an answer
                    q.add(element)
                }
                help()
                lock.unlock()
                return
            } else {
                if (line[index].value?.op == ReqType.Response) { // if request was processed
                    line[index].value = null
                    return
                } else {
                    if (index == 0) {
                        index = postReqAtFreeIndexGetIndex(req)
                    } // leave request
                }
            }
        }
    }

    private fun postReqAtFreeIndexGetIndex(req: Request<E>): Int {
        while (true) {
            if (getIndexLock.tryLock()) {
                for (i in 1 until line.size) {
                    if (line[i].compareAndSet(null, req)) {
                        getIndexLock.unlock()
                        return i
                    }
                }
                getIndexLock.unlock()
                return LINE_SIZE
            }
        }
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
                        val addVal = req.msg
                        q.add(addVal)
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
                line[i].value!!.answer(res)
                s += line[i].value?.op.toString() + " " + line[i].value?.msg.toString() + " at " + i.toString() + " "
            }
        }
        return s
    }

    private fun getHelpedRequest(id: Long): Request<E>? {
        while(true) {
            if (helpLock.tryLock()){
                for (i in 1 until LINE_SIZE) {
                    val req = line[i].value
                    if (req?.id == id) {
                        val ans = line[i].getAndSet(null)
                        helpLock.unlock()
                        return ans
                    }
                }
                helpLock.unlock()
                return null
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

class Request<E>(var op: ReqType, var msg: E?, val id: Long) {
    private val message = msg
    fun answer(newMsg: E?): Request<E> {
        op = ReqType.Response
        msg = newMsg
        return this
    }

    fun getMessage(): E?{
        return message
    }
}

enum class ReqType {
    Poll, Peek, Add, Response
}