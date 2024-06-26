import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fc_array = FCArray<E>(4 * Runtime.getRuntime().availableProcessors())

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val r = Request<E>(Op.POLL)
        handle(r)
        return r.res
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val r = Request<E>(Op.PEEK)
        handle(r)
        return r.res
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        handle(Request(Op.ADD, element))
    }

    private fun handle(request: Request<E>) {
        fc_array.addRequest(request)
        while (true) {
            if (fc_array.tryLock()) {
                for (i in 0 until fc_array.size) {
                    val cell = fc_array.data[i].value
                    if (cell != null) {
                         when(cell.op) {
                            Op.POLL -> {
                                cell.res = q.poll()
                            }
                            Op.PEEK -> {
                                cell.res = q.peek()
                            }
                            Op.ADD -> {
                                q.add(cell.element)
                            }
                        }
                        cell.isFinished = true
                        fc_array.data[i].lazySet(null)
                    }
                }
                fc_array.unlock()
                return
            } else if (request.isFinished) {
                return
            }
        }
    }

    class FCArray<E>(val size: Int) {
        val data = atomicArrayOfNulls<Request<E>>(size)
        private val lock = atomic(false)

        fun tryLock(): Boolean {
            return lock.compareAndSet(expect = false, update = true)
        }

        fun unlock() {
            lock.value = false
        }

        fun addRequest(request: Request<E>) {
            var ind = (0 until size).random()
            while (true) {
                if (data[ind].compareAndSet(null, request)) {
                    return
                }
                ind = (ind + 1) % size
            }
        }
    }

    class Request<E>(val op: Op, val element: E? = null) {
        var isFinished = false
        var res: E? = null
    }

    enum class Op {
        POLL, PEEK, ADD
    }

}