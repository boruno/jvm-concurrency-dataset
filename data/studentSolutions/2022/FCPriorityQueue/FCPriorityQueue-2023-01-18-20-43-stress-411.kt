import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.reflect.KFunction0


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val lock = atomic(0)

    private val fcArray = atomicArrayOfNulls<Job>(FLAT_ARRAY_SIZE)

    private fun tryLock(): Boolean {
        return lock.compareAndSet(0, 1);
    }

    private fun unlock() {
        lock.value = 0;
    }

    private fun operate(status: Status, op: KFunction0<E>): E? {
        val id = id()
        while (true) {
            if (tryLock()) {
                try {
                    return if (q.isEmpty()) {
                        null
                    } else {
                        op()
                    }
                } finally {
                    helpOther()
                    unlock()
                }
            } else {
                val flatJob = Job(status, null)

                if (fcArray[id].compareAndSet(null, flatJob)) {
                    var count = AWAIT_ITERATIONS_COUNT
                    while (count > 0)
                        count--

                    val job = fcArray[id].value
                    if (job != null) {
                        val value = job.e.value
                        if (value != null && fcArray[id].compareAndSet(flatJob, null)) {
                            return value as E
                        }
                    }
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return operate(Status.POLL, q::poll)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return operate(Status.PEEK, q::peek)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val id = id()
        while (true) {
            if (tryLock()) {
                try {
                    q.add(element)
                    return
                } finally {
                    helpOther()
                    unlock()
                }
            } else {
                val flatJob = Job(Status.ADD, element)

                if (fcArray[id].compareAndSet(null, flatJob)) {
                    var count = AWAIT_ITERATIONS_COUNT
                    while (count > 0)
                        count--

                    if (!fcArray[id].compareAndSet(flatJob, null)) {
                        return
                    }
                }
            }
        }
    }

    private fun helpOther() {
        try {
            for (i in 0 until FLAT_ARRAY_SIZE) {
                val job = fcArray[i].value

                if (job != null) {
                    when (job.status) {
                        Status.ADD -> q.add(job.e.value as E)
                        Status.PEEK -> job.e.value = q.peek()
                        else -> job.e.value = q.poll()
                    }
                }
            }
        } finally { }
    }

    private fun id(): Int {
        return Random().nextInt(FLAT_ARRAY_SIZE)
    }
}

class Job(var status: Status, element: Any?) {

    val e: AtomicRef<Any?> = atomic(element)
}

enum class Status {
    POLL, PEEK, ADD
}

private const val AWAIT_ITERATIONS_COUNT = 50
private const val FLAT_ARRAY_SIZE = 10