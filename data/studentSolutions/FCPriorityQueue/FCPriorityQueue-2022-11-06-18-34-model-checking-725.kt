import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>>() {
    val POLL_SIGNAL = "POLL"
    val PEEK_SIGNAL = "PEEK"
    val EMPTY_SIGNAL = "EMPTY"

    class EnqueueRequest<E>(val element: E) {}

    private val q = PriorityQueue<E>()

    val lock = ReentrantLock()
    val FCArray = atomicArrayOfNulls<Any?>(100)

    init {
        for (i in 0 until FCArray.size)
            FCArray[i].compareAndSet(null, EMPTY_SIGNAL)
    }
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (lock.tryLock()) {
            var result = q.poll()
            for (i in 0 until FCArray.size) {
                if (FCArray[i].compareAndSet(POLL_SIGNAL, result)) {
                    result = q.poll()
                    continue
                }
                if (FCArray[i].compareAndSet(PEEK_SIGNAL, result)) {
                    continue
                }
                if (FCArray[i].value is EnqueueRequest<*>) {
                    q.add((FCArray[i].value as EnqueueRequest<E>).element)
                    FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL)
                }
            }
            lock.unlock()
            return result
        } else {
            var i = Random().nextInt(FCArray.size)
            while (!FCArray[i].compareAndSet(EMPTY_SIGNAL, POLL_SIGNAL)) {
                i = Random().nextInt(FCArray.size)
            }
            while (true) {
                if (FCArray[i].value != POLL_SIGNAL) {
                    return FCArray[i].value as E
                }
                if (!lock.isLocked) {
                    if (lock.tryLock()) {
                        if (!FCArray[i].compareAndSet(POLL_SIGNAL, EMPTY_SIGNAL)) {
                            val result = FCArray[i].value
                            FCArray[i].compareAndSet(result, EMPTY_SIGNAL)
                            return result as E
                        }
                        var result = q.poll()
                        for (j in 0 until FCArray.size) {
                            if (FCArray[j].compareAndSet(POLL_SIGNAL, result)) {
                                result = q.poll()
                                continue
                            }
                            if (FCArray[j].compareAndSet(PEEK_SIGNAL, result)) {
                                continue
                            }
                            if (FCArray[j].value is EnqueueRequest<*>) {
                                q.add((FCArray[j].value as EnqueueRequest<E>).element)
                                FCArray[j].compareAndSet(FCArray[j].value, EMPTY_SIGNAL)
                            }
                        }
                        lock.unlock()
                        return result
                    }
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (lock.tryLock()) {
            q.add(element)
            for (i in 0 until FCArray.size) {
                if (FCArray[i].compareAndSet(POLL_SIGNAL, q.poll())) {
                    continue
                }
                if (FCArray[i].compareAndSet(PEEK_SIGNAL, q.peek())) {
                    continue
                }
                if (FCArray[i].value is EnqueueRequest<*>) {
                    q.add((FCArray[i].value as EnqueueRequest<E>).element)
                    FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL)
                }
            }
            lock.unlock()
        } else {
            var i = Random().nextInt(FCArray.size)
            while (!FCArray[i].compareAndSet(EMPTY_SIGNAL, EnqueueRequest(element))) {
                i = Random().nextInt(FCArray.size)
            }
            while (true) {
                if (FCArray[i].value != EnqueueRequest(element)) {
                    return
                }
                if (!lock.isLocked) {
                    if (lock.tryLock()) {
                        if (FCArray[i].value != EnqueueRequest(element)) {
                            return
                        }
                        q.add(element)
                        for (j in 0 until FCArray.size) {
                            if (FCArray[j].compareAndSet(POLL_SIGNAL, q.poll())) {
                                continue
                            }
                            if (FCArray[j].compareAndSet(PEEK_SIGNAL, q.peek())) {
                                continue
                            }
                            if (FCArray[j].value is EnqueueRequest<*>) {
                                q.add((FCArray[j].value as EnqueueRequest<E>).element)
                                FCArray[j].compareAndSet(FCArray[j].value, EMPTY_SIGNAL)
                            }
                        }
                        lock.unlock()
                    }
                }
            }
        }
    }
}