import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = FCArray<E>(4 * Runtime.getRuntime().availableProcessors())

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return handle(Request(Op.POLL))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return handle(Request(Op.PEEK))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        handle(Request(Op.ADD, element))
    }

    private fun handle(request: Request<E>): E? {
        fcArray.addRequest(request)
        while (true) {
            if (fcArray.tryLock()) {
                var res: E? = null
                for (ind in 0 until fcArray.size) {
                    val fcRequest = fcArray.data[ind].value
                    if (fcRequest != null) {
                         when(fcRequest.operation) {
                            Op.POLL -> {
                                res = q.poll()
                            }
                            Op.PEEK -> {
                                res = q.peek()
                            }
                            Op.ADD -> {
                                q.add(fcRequest.element)
                            }
                        }
                        fcRequest.isFinished = true
                        fcArray.data[ind].lazySet(null)
                    }
                }
                fcArray.unlock()
                return res
            } else if (request.isFinished) {
                return null
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

    class Request<E>(val operation: Op, val element: E? = null) {
        var isFinished = false
    }

    enum class Op {
        POLL, PEEK, ADD
    }

}