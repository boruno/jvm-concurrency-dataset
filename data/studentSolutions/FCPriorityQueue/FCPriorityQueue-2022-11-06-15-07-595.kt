import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    val POLL_SIGNAL = "POLL"
    val PEEK_SIGNAL = "PEEK"
    val EMPTY_SIGNAL = "EMPTY"

    private val q = PriorityQueue<E>()

    val lock = ReentrantLock()
    val FCArray = atomicArrayOfNulls<Any?>(100)

    constructor() {
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
                if (FCArray[i].value != EMPTY_SIGNAL) {
                    q.add(FCArray[i].value as E)
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
                            if (FCArray[j].value != EMPTY_SIGNAL) {
                                q.add(FCArray[j].value as E)
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
//        if (lock.tryLock()) { //поправить как poll
//            for (i in FCArray.indices) {
//                if (FCArray[i].equals(POLL_SIGNAL)) {
//                    FCArray[i].compareAndSet(POLL_SIGNAL, q.poll())
//                    continue
//                }
//                if (FCArray[i].compareAndSet(PEEK_SIGNAL, q.peek())) {
//                    continue
//                }
//                if (FCArray[i].value != null) {
//                    q.add(FCArray[i] as E)
//                    FCArray[i].compareAndSet(FCArray[i].value, null)
//                }
//            }
//            lock.unlock()
//            return q.peek()
//        } else {
//            var i = Random().nextInt(FCArray.size)
//            while (!FCArray[i].compareAndSet(null, PEEK_SIGNAL)) {
//                i = Random().nextInt(FCArray.size)
//            }
//            while (true) {
//                if (FCArray[i].value != PEEK_SIGNAL) {
//                    return FCArray[i].value as E
//                }
//                if (!lock.isLocked) {
//                    if (lock.tryLock()) {
//                        FCArray[i].compareAndSet(PEEK_SIGNAL, null)
//                        for (j in FCArray.indices) {
//                            if (FCArray[j].equals(POLL_SIGNAL)) {
//                                FCArray[i].compareAndSet(POLL_SIGNAL, q.poll())
//                                continue
//                            }
//                            if (FCArray[j].compareAndSet(PEEK_SIGNAL, q.peek())) {
//                                continue
//                            }
//                            if (FCArray[j].value != null) {
//                                q.add(FCArray[j] as E)
//                                FCArray[j].compareAndSet(FCArray[j].value, null)
//                            }
//                        }
//                        lock.unlock()
//                        return q.peek()
//                    }
//                }
//            }
//        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (lock.tryLock()) {
            for (i in 0 until FCArray.size) {
                if (FCArray[i].compareAndSet(POLL_SIGNAL, q.poll())) {
                    continue
                }
                if (FCArray[i].compareAndSet(PEEK_SIGNAL, q.peek())) {
                    continue
                }
                if (FCArray[i].value != EMPTY_SIGNAL) {
                    q.add(FCArray[i].value as E)
                    FCArray[i].compareAndSet(FCArray[i].value, EMPTY_SIGNAL)
                }
            }
            q.add(element)
            lock.unlock()
        } else {
            var i = Random().nextInt(FCArray.size)
            while (!FCArray[i].compareAndSet(EMPTY_SIGNAL, element)) {
                i = Random().nextInt(FCArray.size)
            }
            while (true) {
                if (FCArray[i].value == EMPTY_SIGNAL) {
                    return
                }
                if (!lock.isLocked) {
                    if (lock.tryLock()) {
                        if (FCArray[i].compareAndSet(element, EMPTY_SIGNAL)) {
                            return
                        }
                        for (j in 0 until FCArray.size) {
                            if (FCArray[j].compareAndSet(POLL_SIGNAL, q.poll())) {
                                continue
                            }
                            if (FCArray[j].compareAndSet(PEEK_SIGNAL, q.peek())) {
                                continue
                            }
                            if (FCArray[j].value != EMPTY_SIGNAL) {
                                q.add(FCArray[j].value as E)
                                FCArray[j].compareAndSet(FCArray[j].value, EMPTY_SIGNAL)
                            }
                        }
                        q.add(element)
                        lock.unlock()
                    }
                }
            }
        }
    }
}