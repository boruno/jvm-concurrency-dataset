import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

const val ARRAY_SIZE = 2

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arrAdd = atomicArrayOfNulls<E?>(ARRAY_SIZE)
    private val arrPoll = atomicArrayOfNulls<Boolean?>(ARRAY_SIZE)
    private val arrPeek = atomicArrayOfNulls<Boolean?>(ARRAY_SIZE)
    private val arrAddResult = atomicArrayOfNulls<Boolean?>(ARRAY_SIZE)
    private val arrPollResult = atomicArrayOfNulls<E?>(ARRAY_SIZE)
    private val arrPeekResult = atomicArrayOfNulls<E?>(ARRAY_SIZE)
    private val lock = ReentrantLock(true)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var idx = 0
        var wait = false
        while (true) {
            if (lock.tryLock()) {
                if (wait) {
                    if (arrPollResult[idx].value != null) {
                        val res = arrPollResult[idx].value
                        arrPollResult[idx].compareAndSet(res, null)
                        return res
                    } else {
                        arrPoll[idx].compareAndSet(true, null)
                    }
                }

                val res = q.poll()
                help()
                lock.unlock()
                return res
            } else {
                if (!wait) {
                    idx = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
                    if (arrPoll[idx].compareAndSet(null, true)) {
                        wait = true
                    }
                } else {
                    val res = arrPollResult[idx].value
                    if (res != null) {
                        arrPollResult[idx].compareAndSet(res, null)
                        return res
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
        var idx = 0
        var wait = false
        while (true) {
            if (lock.tryLock()) {
                if (wait){
                    if (wait && arrPeekResult[idx].value != null) {
                        val res = arrPeekResult[idx].value
                        arrPeekResult[idx].compareAndSet(res, null)
                        return res
                    } else {
                        arrPeek[idx].compareAndSet(true, null)
                    }
                }

                val res = q.peek()
                help()
                lock.unlock()
                return res
            } else {
                if (!wait) {
                    idx = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
                    if (arrPeek[idx].compareAndSet(null, true)) {
                        wait = true
                    }
                } else {
                    val res = arrPeekResult[idx].value
                    if (res != null) {
                        arrPeekResult[idx].compareAndSet(res, null)
                        return res
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var idx = 0
        var wait = false
        while (true) {
            if (lock.tryLock()) {
                if (wait) {
                    if (wait && arrAddResult[idx].value != null) {
                        arrAddResult[idx].compareAndSet(true, null)
                        return
                    } else {
                        arrAdd[idx].compareAndSet(element, null)
                    }
                }

                q.add(element)
                help()
                lock.unlock()
            } else {
                if (!wait) {
                    idx = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
                    if (arrAdd[idx].compareAndSet(null, element)) {
                        wait = true
                    }
                } else {
                    val res = arrAddResult[idx].value
                    if (res != null) {
                        arrAddResult[idx].compareAndSet(true, null)
                        return
                    }
                }
            }
        }
    }

    private fun help() {
        for (i in 0 until ARRAY_SIZE) {
            val add = arrAdd[i].value
            if (add != null) {
                q.add(add)
                while (!arrAddResult[i].compareAndSet(null, true)) {
                }
                arrAdd[i].compareAndSet(add, null)
            }
            val peek = arrPeek[i].value
            if (peek != null) {
                while (arrPeekResult[i].compareAndSet(null, q.peek())) {
                }
                arrPeek[i].compareAndSet(true, null)
            }
            val poll = arrPoll[i].value
            if (poll != null) {
                while (arrPollResult[i].compareAndSet(null, q.poll())) {
                }
                arrPoll[i].compareAndSet(true, null)
            }
        }
    }
}