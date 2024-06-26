import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    val POLL_SIGNAL = "POLL"
    val PEEK_SIGNAL = "PEEK"
    val EMPTY_SIGNAL = "EMPTY"

    class EnqueueRequest<E>(val element: E) {}
    class DequeueResponse<E>(val element: E) {}

    private val q = PriorityQueue<E>()

    val lock = atomic(false)
    // todo  попробовать вместо массива FAAQueue использовать (когда туда добавляем операцию, нам возвращают индекс,
    //  по которому мы ее положили, и дальше мы по нему, enqIdx и deqIdx понимаем, взяли ли наш запрос
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
//        while (true) {
//            if (lock.tryLock()) {
//                val result = q.poll()
//                lock.unlock()
//                return result
//            }
//        }
        if (lock.compareAndSet(false, true)) {
            for (i in 0 until FCArray.size) {
                if (FCArray[i].equals(POLL_SIGNAL)) {
                    FCArray[i].compareAndSet(POLL_SIGNAL, q.poll())
                    continue
                }
                if (FCArray[i].equals(PEEK_SIGNAL)) {
                    FCArray[i].compareAndSet(PEEK_SIGNAL, q.peek())
                    continue
                }
                if (FCArray[i].value is EnqueueRequest<*>) {
                    q.add((FCArray[i].value as EnqueueRequest<E>).element)
                    FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL)
                }
            }
            val result = q.poll()
            lock.compareAndSet(true, false)
            return result
        } else {
            var i = Random().nextInt(FCArray.size)
            while (!FCArray[i].compareAndSet(EMPTY_SIGNAL, POLL_SIGNAL)) {
                i = Random().nextInt(FCArray.size)
            }
            while (true) {
                if (FCArray[i].value is DequeueResponse<*>) {
                    val result = FCArray[i].value
                    FCArray[i].compareAndSet(result, EMPTY_SIGNAL)
                    return (result as DequeueResponse<E>).element
                }
                if (lock.equals(false)) {
                    if (lock.compareAndSet(false, true)) {
                        if (FCArray[i].value is DequeueResponse<*>) {
                            val result = FCArray[i].value
                            FCArray[i].compareAndSet(result, EMPTY_SIGNAL)
                            lock.compareAndSet(true, false)
                            return (result as DequeueResponse<E>).element
                        }
                        FCArray[i].compareAndSet(POLL_SIGNAL, EMPTY_SIGNAL)
                        for (j in 0 until FCArray.size) {
                            if (FCArray[j].equals(POLL_SIGNAL)) {
                                FCArray[j].compareAndSet(POLL_SIGNAL, DequeueResponse(q.poll()))
                                continue
                            }
                            if (FCArray[j].equals(PEEK_SIGNAL)) {
                                FCArray[j].compareAndSet(PEEK_SIGNAL, DequeueResponse(q.peek()))
                                continue
                            }
                            if (FCArray[j].value is EnqueueRequest<*>) {
                                q.add((FCArray[j].value as EnqueueRequest<E>).element)
                                FCArray[j].compareAndSet(FCArray[j].value, EMPTY_SIGNAL)
                            }
                        }
                        val result = q.poll()
                        lock.compareAndSet(true, false)
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
        //мб вообще тут ничего не надо, просто из очереди брать?
//        return q.peek()
//        while (true) {
//            if (lock.tryLock()) {
//                val result = q.peek()
//                lock.unlock()
//                return result
//            }
//        }
        if (lock.compareAndSet(false, true)) {
            for (i in 0 until FCArray.size) {
                if (FCArray[i].equals(POLL_SIGNAL)) {
                    FCArray[i].compareAndSet(POLL_SIGNAL, q.poll())
                    continue
                }
                if (FCArray[i].equals(PEEK_SIGNAL)) {
                    FCArray[i].compareAndSet(PEEK_SIGNAL, q.peek())
                    continue
                }
                if (FCArray[i].value is EnqueueRequest<*>) {
                    q.add((FCArray[i].value as EnqueueRequest<E>).element)
                    FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL)
                }
            }
            val result = q.peek()
            lock.compareAndSet(true, false)
            return result
        } else {
            var i = Random().nextInt(FCArray.size)
            while (!FCArray[i].compareAndSet(EMPTY_SIGNAL, PEEK_SIGNAL)) {
                i = Random().nextInt(FCArray.size)
            }
            while (true) {
                if (FCArray[i].value is DequeueResponse<*>) {
                    val result = FCArray[i].value
                    FCArray[i].compareAndSet(result, EMPTY_SIGNAL)
                    return (result as DequeueResponse<E>).element
                }
                if (lock.equals(false)) {
                    if (lock.compareAndSet(false, true)) {
                        if (FCArray[i].value is DequeueResponse<*>) {
                            val result = FCArray[i].value
                            FCArray[i].compareAndSet(result, EMPTY_SIGNAL)
                            lock.compareAndSet(true, false)
                            return (result as DequeueResponse<E>).element
                        }
                        FCArray[i].compareAndSet(PEEK_SIGNAL, EMPTY_SIGNAL)
                        for (j in 0 until FCArray.size) {
                            if (FCArray[j].equals(POLL_SIGNAL)) {
                                FCArray[j].compareAndSet(POLL_SIGNAL, DequeueResponse(q.poll()))
                                continue
                            }
                            if (FCArray[j].equals(PEEK_SIGNAL)) {
                                FCArray[j].compareAndSet(PEEK_SIGNAL, DequeueResponse(q.peek()))
                                continue
                            }
                            if (FCArray[j].value is EnqueueRequest<*>) {
                                q.add((FCArray[j].value as EnqueueRequest<E>).element)
                                FCArray[j].compareAndSet(FCArray[j].value, EMPTY_SIGNAL)
                            }
                        }
                        val result = q.peek()
                        lock.compareAndSet(true, false)
                        return result
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
//        while (true) {
//            if (lock.tryLock()) {
//                q.add(element)
//                lock.unlock()
//                return
//            }
//        }
        if (lock.compareAndSet(false, true)) {
            for (i in 0 until FCArray.size) {
                if (FCArray[i].equals(POLL_SIGNAL)) {
                    FCArray[i].compareAndSet(POLL_SIGNAL, DequeueResponse(q.poll()))
                    continue
                }
                if (FCArray[i].equals(PEEK_SIGNAL)) {
                    FCArray[i].compareAndSet(PEEK_SIGNAL, DequeueResponse(q.peek()))
                    continue
                }
                if (FCArray[i].value is EnqueueRequest<*>) {
                    q.add((FCArray[i].value as EnqueueRequest<E>).element)
                    FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL)
                }
            }
            q.add(element)
            lock.compareAndSet(true, false)
            return
        } else {
            var i = Random().nextInt(FCArray.size)
            while (!FCArray[i].compareAndSet(EMPTY_SIGNAL, EnqueueRequest(element))) {
                i = Random().nextInt(FCArray.size)
            }
            while (true) {
                if (FCArray[i].value !is EnqueueRequest<*>) { // ?
                    return
                }
                if (lock.equals(false)) {
                    if (lock.compareAndSet(false, true)) {
                        if (FCArray[i].value !is EnqueueRequest<*>) {
                            lock.compareAndSet(true, false)
                            return
                        }
                        FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL) // ?
                        for (j in 0 until FCArray.size) {
                            if (FCArray[j].equals(POLL_SIGNAL)) {
                                FCArray[j].compareAndSet(POLL_SIGNAL, q.poll())
                                continue
                            }
                            if (FCArray[j].equals(PEEK_SIGNAL)) {
                                FCArray[j].compareAndSet(PEEK_SIGNAL, q.peek())
                                continue
                            }
                            if (FCArray[j].value is EnqueueRequest<*>) {
                                q.add((FCArray[j].value as EnqueueRequest<E>).element)
                                FCArray[j].compareAndSet(FCArray[j].value, EMPTY_SIGNAL)
                            }
                        }
                        q.add(element)
                        lock.compareAndSet(true, false)
                        return
                    }
                }
            }
        }
    }
}