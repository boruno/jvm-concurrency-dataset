import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val workers = 4 * Runtime.getRuntime().availableProcessors()
    private val works: AtomicArray<Work<E>?> = atomicArrayOfNulls(workers)
    private val rngGen = Random()
    private val lock = ReentrantLock()

//    private val POLL = 0
//    private val PEEK = 1
//    private val ADD = 2
//    private val DONE = 3

    class Work<E>(var op: Int, var value: E? = null)


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val e = Work<E>(0)
        while (true) {
            val id = rngGen.nextInt(workers)
            if (works[id].compareAndSet(null, e)) {
                require(works[id].value == e)
                while (true) {
                    if (lock.tryLock()) {
                        for (i in 0 until workers) {
                            val w = works[i].value ?: continue
                            if (w.op != 3) {
                                when (w.op) {
                                    2 -> {
                                        q.add(w.value)
                                    }
                                    0 -> {
                                        w.value = q.poll()
                                    }
                                    1 -> {
                                        w.value = q.peek()
                                    }
                                }
                                w.op = 3
                            }
                        }
                        lock.unlock()
                    }
                    if (e.op == 3) {
                        val value = e.value
                        works[id].value = null
                        return value
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
        val e = Work<E>(1)
        while (true) {
            val id = rngGen.nextInt(workers)
            if (works[id].compareAndSet(null, e)) {
                require(works[id].value == e)
                while (true) {
                    if (lock.tryLock()) {
                        for (i in 0 until workers) {
                            val w = works[i].value ?: continue
                            if (w.op != 3) {
                                when (w.op) {
                                    2 -> {
                                        q.add(w.value)
                                    }
                                    0 -> {
                                        w.value = q.poll()
                                    }
                                    1 -> {
                                        w.value = q.peek()
                                    }
                                }
                                w.op = 3
                            }
                        }
                        lock.unlock()
                    }
                    if (e.op == 3) {
                        val value = e.value
                        works[id].value = null
                        return value
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val work = Work(2, element)
        while (true) {
            val randomIndex = rngGen.nextInt(workers)
            if (works[randomIndex].compareAndSet(null, work)) {
                require(works[randomIndex].value == work)
                while (true) {
                    if (lock.tryLock()) {
                        for (i in 0 until workers) {
                            val currWork = works[i].value ?: continue
                            if (currWork.op != 3) {
                                when (currWork.op) {
                                    2 -> {
                                        q.add(currWork.value)
                                    }
                                    0 -> {
                                        currWork.value = q.poll()
                                    }
                                    1 -> {
                                        currWork.value = q.peek()
                                    }
                                }
                                currWork.op = 3
                            }
                        }
                        lock.unlock()
                    }
                    if (work.op == 3) {
                        works[randomIndex].value = null
                        return
                    }
                }
            }
        }
    }
}
