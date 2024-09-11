import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    val POLL_SIGNAL = "POLL"
    val EMPTY_SIGNAL = "EMPTY"

//    class EnqueueRequest<E>(val element: E) {}
    class DequeueResponse<E>(val element: E) {}

    private val q = PriorityQueue<E>()

    val locked = atomic(false)
    // todo  попробовать вместо массива FAAQueue использовать (когда туда добавляем операцию, нам возвращают индекс,
    //  по которому мы ее положили, и дальше мы по нему, enqIdx и deqIdx понимаем, взяли ли наш запрос

    fun tryLock() = locked.compareAndSet(false, true)
    fun unlock() { locked.value = false }

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
//            if (tryLock()) {
//                val result = q.poll()
//                unlock()
//                return result
//            }
//        }
        if (tryLock()) {
            for (i in 0 until FCArray.size) {
                if (FCArray[i].equals(POLL_SIGNAL)) {
                    FCArray[i].compareAndSet(POLL_SIGNAL, q.poll())
                    continue
                }
//                if (FCArray[i].value is EnqueueRequest<*>) {
//                    q.add((FCArray[i].value as EnqueueRequest<E>).element)
//                    FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL)
//                }
            }
            val result = q.poll()
            unlock()
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
                if (locked.equals(false)) {
                    if (tryLock()) {
                        if (FCArray[i].value is DequeueResponse<*>) {
                            val result = FCArray[i].value
                            FCArray[i].compareAndSet(result, EMPTY_SIGNAL)
                            unlock()
                            return (result as DequeueResponse<E>).element
                        }
                        FCArray[i].compareAndSet(POLL_SIGNAL, EMPTY_SIGNAL)
                        for (j in 0 until FCArray.size) {
                            if (FCArray[j].equals(POLL_SIGNAL)) {
                                FCArray[j].compareAndSet(POLL_SIGNAL, DequeueResponse(q.poll()))
                                continue
                            }
//                            if (FCArray[j].value is EnqueueRequest<*>) {
//                                q.add((FCArray[j].value as EnqueueRequest<E>).element)
//                                FCArray[j].compareAndSet(FCArray[j].value, EMPTY_SIGNAL)
//                            }
                        }
                        val result = q.poll()
                        unlock()
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
        while (true) {
            if (tryLock()) {
                q.add(element)
                unlock()
                return
            }
        }
//        if (tryLock()) {
//            for (i in 0 until FCArray.size) {
//                if (FCArray[i].equals(POLL_SIGNAL)) {
//                    FCArray[i].compareAndSet(POLL_SIGNAL, DequeueResponse(q.poll()))
//                    continue
//                }
//                if (FCArray[i].value is EnqueueRequest<*>) {
//                    q.add((FCArray[i].value as EnqueueRequest<E>).element)
//                    FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL)
//                }
//            }
//            q.add(element)
//            unlock()
//            return
//        } else {
//            var i = Random().nextInt(FCArray.size)
//            while (!FCArray[i].compareAndSet(EMPTY_SIGNAL, EnqueueRequest(element))) {
//                i = Random().nextInt(FCArray.size)
//            }
//            while (true) {
//                if (FCArray[i].value !is EnqueueRequest<*>) { // ?
//                    return
//                }
//                if (locked.equals(false)) {
//                    if (tryLock()) {
//                        if (FCArray[i].value !is EnqueueRequest<*>) {
//                            unlock()
//                            return
//                        }
//                        FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL) // ?
//                        for (j in 0 until FCArray.size) {
//                            if (FCArray[j].equals(POLL_SIGNAL)) {
//                                FCArray[j].compareAndSet(POLL_SIGNAL, q.poll())
//                                continue
//                            }
//                            if (FCArray[j].value is EnqueueRequest<*>) {
//                                q.add((FCArray[j].value as EnqueueRequest<E>).element)
//                                FCArray[j].compareAndSet(FCArray[j].value, EMPTY_SIGNAL)
//                            }
//                        }
//                        q.add(element)
//                        unlock()
//                        return
//                    }
//                }
//            }
//        }
    }
}