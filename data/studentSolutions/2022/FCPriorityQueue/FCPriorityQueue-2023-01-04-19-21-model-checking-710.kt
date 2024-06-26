import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val SIZE = 10
    private val fc_array = atomicArrayOfNulls<Any?>(SIZE)
    private val lock = MyLock()//ReentrantLock()

    private class MyLock() {//reentrant lock gives a compilation error for some reason so i just did this
        private val lock = atomic(false)
        fun tryLock() : Boolean {
            return lock.compareAndSet(false, true);
        }

        fun unlock() {
            lock.getAndSet(false)
        }
    }

    private class enq_op(val x : Any)
    private class deq_op
    private class peek_op
    private class okdone
    private class result(val x : Any?)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */

    fun combiner() {
        for (i in 0..SIZE - 1) {
            while (true) {
                val v = fc_array[i].value
                if (v is enq_op) {
                    if (!fc_array[i].compareAndSet(v, okdone())) {
                        continue
                    }
                    q.add(v.x as E)
                } else if (v is peek_op) {
                    if (!fc_array[i].compareAndSet(v, result(q.peek()))) {
                        continue
                    }
                } else if (v is deq_op) {
                    if (!fc_array[i].compareAndSet(v, result(q.poll()))) { //should be no reason for this to continue?
                        println("oh shit")
                        continue
                    }
                }
                break
            }
        }
    }

    fun poll(): E? {
        while (true) {
            if (lock.tryLock()) {
                val ans = q.poll();
                combiner();
                lock.unlock();
                return ans
            }
            val pos = ThreadLocalRandom.current().nextInt(SIZE);
            if (fc_array[pos].compareAndSet(null, deq_op())) {
                while (true) {
                    var p = fc_array[pos].value;
                    if (p is result && fc_array[pos].compareAndSet(p, null)) {
                        return p.x as E?
                    }
                    if (lock.tryLock()) {
                        p = fc_array[pos].value;
                        val res : E?
                        if (p is result && fc_array[pos].compareAndSet(p, null)) {
                            res = p.x as E?
                        } else {
                            res = q.poll()
                        }
                        combiner()
                        lock.unlock()
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
        while (true) {
            if (lock.tryLock()) {
                val ans = q.peek();
                combiner();
                lock.unlock();
                return ans
            }
            val pos = ThreadLocalRandom.current().nextInt(SIZE);
            if (fc_array[pos].compareAndSet(null, peek_op())) {
                while (true) {
                    var p = fc_array[pos].value;
                    if (p is result && fc_array[pos].compareAndSet(p, null)) {
                        return p.x as E?
                    }
                    if (lock.tryLock()) {
                        p = fc_array[pos].value;
                        var res : E?
                        if (p is result && fc_array[pos].compareAndSet(p, null)) {
                            res = p.x as E?
                        } else {
                            res = q.peek()
                        }
                        combiner()
                        lock.unlock()
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
        while (true) {
            if (lock.tryLock()) {
                q.add(element);
                combiner();
                lock.unlock();
                return
            }
            val pos = ThreadLocalRandom.current().nextInt(SIZE);
            if (fc_array[pos].compareAndSet(null, enq_op(element))) {
                while (true) {
                    if (fc_array[pos].compareAndSet(okdone(), null)) {
                        return
                    }
                    if (lock.tryLock()) {
                        fc_array[pos].compareAndSet(okdone(), null) //just in case
                        combiner()
                        lock.unlock()
                        return
                    }
                }
            }
        }
    }
}