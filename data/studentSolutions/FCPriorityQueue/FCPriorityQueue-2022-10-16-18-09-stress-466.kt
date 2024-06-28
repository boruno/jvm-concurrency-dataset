import kotlinx.atomicfu.AtomicInt
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayDeque
import kotlin.concurrent.*


class FCPriorityQueue<E : Comparable<E>> {
    private val LINE_SIZE = 75
    private val line = atomicArrayOfNulls<Request<E>>(LINE_SIZE)
    private val q = PriorityQueue<E>()
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
                var leftMsg = line[index].getAndSet(null)
                val ans: E? = if (leftMsg?.op != ReqType.Response) {
                    q.poll()
                } else {
                    leftMsg.getMessage()
                }
                help()
                lock.unlock()
                return ans
            } else {
                var leftMsg = line[index].value
                if (leftMsg?.op == ReqType.Response) {
                    line[index].value = null
                    return leftMsg.getMessage()
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
        val req = Request<E>(ReqType.Peek, null)
        var index = 0
        while (true) {
            if (lock.tryLock()) {
                var leftMsg = line[index].getAndSet(null)
                val ans: E? = if (leftMsg?.op != ReqType.Response) {
                    q.peek()
                } else {
                    leftMsg.getMessage()
                }
                help()
                lock.unlock()
                return ans
            } else {
                var leftMsg = line[index].value
                if (leftMsg?.op == ReqType.Response) {
                    line[index].value = null
                    return leftMsg.getMessage()
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
        val req = Request<E>(ReqType.Add, element)
        var index = 0
        while (true) {
            if (lock.tryLock()) {
                var leftMsg = line[index].getAndSet(null)
                if (leftMsg?.op != ReqType.Response) {
                    q.add(element)
                } else {

                }
                help()
                lock.unlock()
                return
            } else {
                var leftMsg = line[index].value
                if (leftMsg?.op == ReqType.Response) {
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
        for (i in 1 until line.size) {
            if (line[i].compareAndSet(null, req)) {
                return i
            }
        }
        return LINE_SIZE
    }

    private fun help() {
        for (i in 1 until LINE_SIZE) {
            val req = line[i].value
            if (req != null) {
                var res: E?
                when (req.op) {
                    ReqType.Add -> {
                        q.add(req.getMessage())
                        line[i].value!!.answer(null)
                    }

                    ReqType.Poll -> {
                        line[i].value!!.answer(q.poll())
                    }

                    ReqType.Peek -> {
                        line[i].value!!.answer(q.peek())
                    }

                    else -> {
                    }
                }
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

class Request<E>(var op: ReqType, var msg: E?) {
    private val message = atomic<E?>(msg)
    fun answer(newMsg: E?): Request<E> {
        op = ReqType.Response
        message.value = newMsg
        return this
    }

    fun getMessage(): E? {
        return message.value
    }
}

enum class ReqType(val value: Int) {
    Poll(1), Peek(2), Add(3), Response(0)
}