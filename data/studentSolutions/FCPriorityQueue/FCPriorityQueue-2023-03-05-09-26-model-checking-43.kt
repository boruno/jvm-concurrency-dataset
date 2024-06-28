import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.AtomicArray
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.locks.ReentrantLock


//fun processJobs(jobs: AtomicArray<Any?>) {
////    for (i in 0 until jobs.size) {
////        val job = jobs[i].value
////        if (job is PROCESS) {
////            jobs[i].compareAndSet(task, FINISH(task.task()))
////        }
////    }
//    for (i in 0..jobs.size-1) {
//        val job = jobs[i].value
//        if (job is PROCESS) {
//            jobs[i].getAndSet(FINISH(job.job()))
//        }
//    }
//}


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
//    private val lock = atomic(false)
    private val lock = ReentrantLock()
    private val jobs = atomicArrayOfNulls<Any?>(ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return run { q.poll() } as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return run { q.peek() } as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        run { q.add(element) }
    }

    private fun run(job: () -> Any?): Any? {
        if (lock.tryLock()) {
            val result = job()
            processJobs()
            lock.unlock()
            return result
        }
        val i = Thread.currentThread().id.toInt() % ARRAY_SIZE
        while (!jobs[i].compareAndSet(null, PROCESS(job))) {}
        while (true) {
            var result = jobs[i].value
            if (result is FINISH) {
                jobs[i].compareAndSet(result, null)
                return result.result
            }
            if (lock.tryLock()) {
                result = jobs[i].value
                jobs[i].compareAndSet(result, null)
                result = if (result is FINISH) result.result else (result as PROCESS).job()
                processJobs()
                lock.unlock()
                return result
            }
        }
    }

//    private fun runJobs() {
//        for (i in 0 until jobs.size) {
//            val job = jobs[i].value
//            if (job is PROCESS) {
//                jobs[i].compareAndSet(task, FINISH(task.task()))
//            }
//        }
//        for (i in 0..tasks.size-1) {
//            val f = tasks[i].value
//            if (f is ACTION) {
//                tasks[i].getAndSet(RESULT(f.act()))
//            }
//        }
//    }
    private fun processJobs() {
    //    for (i in 0 until jobs.size) {
    //        val job = jobs[i].value
    //        if (job is PROCESS) {
    //            jobs[i].compareAndSet(task, FINISH(task.task()))
    //        }
    //    }
        for (i in 0..jobs.size-1) {
            val job = jobs[i].value
            if (job is PROCESS) {
                jobs[i].getAndSet(FINISH(job.job()))
            }
        }
    }

//    private fun tryLock(): Boolean {
//        return lock.compareAndSet(false, true)
//    }
//
//    private fun unlock() {
//        lock.compareAndSet(true, false)
//    }
}


private class PROCESS(val job: () -> Any?)
private class FINISH(val result: Any?)


const val ARRAY_SIZE = 10