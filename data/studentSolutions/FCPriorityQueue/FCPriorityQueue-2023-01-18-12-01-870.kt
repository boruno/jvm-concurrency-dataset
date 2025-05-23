import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = FCArray<E>(64)

    fun poll(): E? {
        return publishAndWaitResult(Request { q.poll() }).value
    }

    fun peek(): E? {
        return publishAndWaitResult(Request { q.peek() }).value
    }

    fun add(element: E) {
        publishAndWaitResult(Request { q.add(element); null })
    }

    private fun publishAndWaitResult(request: Request<E>): Request<E> {
        fcArray.add(request)
        while (true) {
            if (fcArray.tryLock()) {
                combinerRun()
                fcArray.unlock()
                return request
            }
            if (request.isDone) {
                return request
            }
        }
    }

    // only 1 thread do this
    private fun combinerRun() {
        for (ind in 0 until fcArray.size) {
            val request = fcArray.data[ind].value
            if (request != null) {
                request.invoke()
                // Anyone else try to CAS from null but,
                // no one else can't set null at current time, so we don't need CAS.
                fcArray.data[ind].value = null
            }
        }
    }

    class FCArray<E>(val size: Int) {
        private val threadCnt = atomic(0)
        private val index = ThreadLocal.withInitial { threadCnt.getAndIncrement() % 64 }
        val data = atomicArrayOfNulls<Request<E>>(size)
        private val lock = atomic(false)

        fun tryLock(): Boolean {
            return lock.compareAndSet(expect = false, update = true)
        }

        fun unlock() {
            lock.value = false
        }

        fun add(request: Request<E>) {
            data[index.get()].compareAndSet(null, request)
        }
    }

    class Request<E>(@Volatile var operation: () -> E?) {
        @Volatile
        var isDone = false

        @Volatile
        var value: E? = null

        fun invoke() {
            this.value = operation.invoke()
            this.isDone = true
        }
    }
}
