import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import kotlin.reflect.KFunction0


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val lock = atomic(0)

    private val fcArray = atomicArrayOfNulls<Job>(FLAT_ARRAY_SIZE)

    private fun tryLock(): Boolean {
        return lock.compareAndSet(0, 1)
    }

    private fun unlock() {
        lock.value = 0
    }

    private fun operate(status: Status, op: KFunction0<E>): E? {
        val flatJob = Job(status, null)

        val id = id()
        while (true) {
            if (tryLock()) {
                try {
                    val job = fcArray[id].value;
                    if (job == flatJob) {
                        fcArray[id].value = null
                        if (job.e != null) {
                            return job.e as E
                        }
                    }

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
                fcArray[id].compareAndSet(null, flatJob);
            }

            val job = fcArray[id].value;
            if (job == flatJob && job.e != null && fcArray[id].compareAndSet(job, null)) {
                return job.e as E
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
        val flatJob = Job(Status.ADD, element)

        val id = id()
        while (true) {
            if (tryLock()) {
                try {
                    val job = fcArray[id].value;
                    if (job == flatJob) {
                        fcArray[id].value = null
                        if (job.e == null) {
                            return
                        }
                    }

                    q.add(element)
                    return
                } finally {
                    helpOther()
                    unlock()
                }
            } else {
                fcArray[id].compareAndSet(null, flatJob)
            }

            val job = fcArray[id].value;
            if (job == flatJob && job.e == null && fcArray[id].compareAndSet(job, null)) {
                return
            }
        }
    }

    private fun helpOther() {
        try {
            for (i in 0 until FLAT_ARRAY_SIZE) {
                val job = fcArray[i].value

                if (job != null) {
                    when (job.status) {
                        Status.ADD -> {
                            q.add(job.e as E)
                            job.e = null;
                            fcArray[i].value = job
                        }

                        Status.PEEK -> {
                            job.e = q.peek()
                            fcArray[i].value = job
                        }

                        else -> {
                            job.e = q.peek()
                            fcArray[i].value = job
                        }
                    }
                }
            }
        } finally {
        }
    }

    private fun id(): Int {
        return Random().nextInt(FLAT_ARRAY_SIZE)
    }
}

class Job(val status: Status, var e: Any?)

enum class Status {
    POLL, PEEK, ADD
}

private const val FLAT_ARRAY_SIZE = 10