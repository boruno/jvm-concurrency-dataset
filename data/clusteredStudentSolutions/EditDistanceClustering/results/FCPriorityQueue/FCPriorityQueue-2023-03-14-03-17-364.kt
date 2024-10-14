import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    val locked = atomic(false)
    val FCArray = Array(10) { AtomicReference<Request<E>?>(null) }

    fun tryLock() = locked.compareAndSet(false, true)

    fun unlock() {
        locked.value = false
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val isCombiner = tryLock();
        if (isCombiner) return exclusivePoll()

        val request = PollRequest<E>(null, Status.WAITING)
        val index = putRequest(request)
        val finishedRequest = waitForResultOrLock(index)
        if (finishedRequest.status == Status.IN_LOCK)
            return exclusivePoll()
        return finishedRequest.value
    }

    private fun exclusivePoll(): E? {
        val element = q.poll()
        fulfillRequests()
        return element;
    }

    private fun fulfillRequests() {
        FCArray.forEach {
            val request = it.get()
            if (request != null) {
                if (request.status == Status.WAITING) {
                    val finishedRequest = request.fulfillRequest(q)
                    it.compareAndSet(request, finishedRequest)
                }
            }
        }
        unlock()
    }

    private fun putRequest(request: Request<E>): Int {
        var index = ThreadLocalRandom.current().nextInt(10)
        var ifPut = FCArray[index].compareAndSet(null, request)
        while (!ifPut) {
            index = ThreadLocalRandom.current().nextInt(10)
            ifPut = FCArray[index].compareAndSet(null, request)
        }
        return index
    }

    private fun waitForResultOrLock(index: Int): Request<E> {
        while (!tryLock()) {
            val finished = checkIfFinished(index) ?: continue
            return finished
        }

        val finishedAndLock = checkIfFinished(index)
        if (finishedAndLock != null) {
            unlock()
            return finishedAndLock
        }

        FCArray[index].compareAndSet(FCArray[index].get()!!, null)
        return DefaultRequest(null, Status.IN_LOCK)
    }

    private fun checkIfFinished(index: Int) : Request<E>? {
        val request = FCArray[index].get()
        if (request!!.status == Status.FiNISHED) {
            FCArray[index].compareAndSet(request, null)
            return request
        }
        return null
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val isCombiner = tryLock();
        if (isCombiner) return exclusivePeek()

        val request = PeekRequest<E>(null, Status.WAITING)
        val index = putRequest(request)
        val finishedRequest = waitForResultOrLock(index)
        if (finishedRequest.status == Status.IN_LOCK)
            return exclusivePeek()
        return finishedRequest.value
    }

    private fun exclusivePeek(): E? {
        val element = q.peek()
        fulfillRequests()
        return element;
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val isCombiner = tryLock();
        if (isCombiner) {
            exclusiveAdd(element)
            return
        }

        val request = AddRequest<E>(null, Status.WAITING)
        val index = putRequest(request)
        val finishedRequest = waitForResultOrLock(index)
        if (finishedRequest.status == Status.IN_LOCK)
            exclusiveAdd(element)
    }

    private fun exclusiveAdd(element: E) {
        q.add(element)
        fulfillRequests()
    }


    enum class Status {
        WAITING,
        FiNISHED,
        IN_LOCK
    }

    abstract class Request<E>(val value: E?, val status: Status) {
        abstract fun fulfillRequest(q: PriorityQueue<E>): Request<E>
    }

    class PollRequest<E>(value: E?, status: Status) : Request<E>(value, status) {
        override fun fulfillRequest(q: PriorityQueue<E>): Request<E> {
            return PollRequest(q.poll(), Status.FiNISHED)
        }
    }

    class PeekRequest<E>(value: E?, status: Status) : Request<E>(value, status) {
        override fun fulfillRequest(q: PriorityQueue<E>): Request<E> {
            return PeekRequest(q.peek(), Status.FiNISHED)
        }
    }

    class AddRequest<E>(value: E?, status: Status) : Request<E>(value, status) {
        override fun fulfillRequest(q: PriorityQueue<E>): Request<E> {
            q.add(value)
            return AddRequest(null, Status.FiNISHED)
        }
    }

    class DefaultRequest<E>(value: E?, status: Status) : Request<E>(value, status) {
        override fun fulfillRequest(q: PriorityQueue<E>): Request<E> {
            return this
        }
    }
}