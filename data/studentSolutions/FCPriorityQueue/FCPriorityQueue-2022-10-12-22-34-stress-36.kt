import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.ArrayDeque

class FCPriorityQueue<E : Comparable<E>> {
    private val line = atomicArrayOfNulls<Request<E>>(100)
    private val q = PriorityQueue<E>()
    private val lock = Lock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if(!lock.tryLock()) {
            var index = 0
            val req = Request<E>(ReqType.Poll)
            while (!line[index].compareAndSet(null, req)) {
                index++;
            }
            val found: E?
            while (line[index].value == req) { // while request is being processed
                if (!lock.locked.value) {
                    line[index].compareAndSet(req, null)
                    break
                }
            }
            found = line[index].value?.value
            line[index].compareAndSet(Request(ReqType.Response, found), null)
            return found
        }
        help()
        lock.unlock()
        return q.poll()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if(!lock.tryLock()) {
            var index = 0
            val req = Request<E>(ReqType.Peek)
            while (!line[index].compareAndSet(null, req)) {
                index++;
            }
            val found: E?
            while (line[index].value == req) { // while request is being processed
                if (!lock.locked.value) {
                    line[index].compareAndSet(req, null)
                    break
                }
            }
            found = line[index].value?.value
            line[index].compareAndSet(Request(ReqType.Response, found), null)
            return found
        }
        help()
        lock.unlock()
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        // got lock
        // no lock :(
        if(!lock.tryLock()) {
            var index = 0
            val req = Request(ReqType.Add, element)
            while (!line[index].compareAndSet(null, req)) {
                index++;
            }
            while (line[index].value == req) { // while request is being processed
                if (!lock.locked.value) {
                    line[index].compareAndSet(req, null)
                    break
                }
            }
            return
        }
        help()
        lock.unlock()
        q.add(element)
        return
    }

    private fun help() {
        for(i in 0 .. line.size) {
            val req = line[i].value
            if (req != null)
            {
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

                    else -> {res = null}
                }
                line[i].value = Request(ReqType.Response, res)
            }
        }
    }
}

class Lock {
    val locked = atomic(false)

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