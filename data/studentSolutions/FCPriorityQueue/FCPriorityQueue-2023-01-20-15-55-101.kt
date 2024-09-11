import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val rngGen = Random()
    private val lock = ReentrantLock()
    private val priorityQueue = PriorityQueue<E>()
    private val workers = 4 * Runtime.getRuntime().availableProcessors()
    private val works: AtomicArray<Work<E>?> = atomicArrayOfNulls(workers)

    private val POLL = 0
    private val PEEK = 1
    private val ADD = 2
    private val DONE = 3

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val work = Work<E>(POLL)
        while (true) {
            val randomIndex = rngGen.nextInt(workers)
//            println(randomIndex)
            if (works[randomIndex].compareAndSet(null, work)) {
                require(works[randomIndex].value == work)
                while (true) {
                    if (lock.tryLock()) {
                        for (i in 0 until workers) {
                            val currWork = works[i].value ?: continue
                            if (currWork.op != DONE) {
                                when (currWork.op) {
                                    ADD -> {
                                        priorityQueue.add(currWork.value)
                                    }
                                    POLL -> {
                                        currWork.value = priorityQueue.poll()
                                    }
                                    PEEK -> {
                                        currWork.value = priorityQueue.peek()
                                    }
                                }
                                currWork.op = DONE
                            }
                        }
                        lock.unlock()
                    }
//                        println(work.op)
                    if (work.op == DONE) {
                        val res = work.value
                        works[randomIndex].value = null
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
        val work = Work<E>(PEEK)
        while (true) {
            val randomIndex = rngGen.nextInt(workers)
//            println(randomIndex)
            if (works[randomIndex].compareAndSet(null, work)) {
                require(works[randomIndex].value == work)
                while (true) {
                    if (lock.tryLock()) {
                        for (i in 0 until workers) {
                            val currWork = works[i].value ?: continue
                            if (currWork.op != DONE) {
                                when (currWork.op) {
                                    ADD -> {
                                        priorityQueue.add(currWork.value)
                                    }
                                    POLL -> {
                                        currWork.value = priorityQueue.poll()
                                    }
                                    PEEK -> {
                                        currWork.value = priorityQueue.peek()
                                    }
                                }
                                currWork.op = DONE
                            }
                        }
                        lock.unlock()
                    }
//                        println(work.op)
                    if (work.op == DONE) {
                        val res = work.value
                        works[randomIndex].value = null
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
        val work = Work(ADD, element)
        while (true) {
            val randomIndex = rngGen.nextInt(workers)
//            println(randomIndex)
            if (works[randomIndex].compareAndSet(null, work)) {
                require(works[randomIndex].value == work)
                while (true) {
                    if (lock.tryLock()) {
                        for (i in 0 until workers) {
                            val currWork = works[i].value ?: continue
                            if (currWork.op != DONE) {
                                when (currWork.op) {
                                    ADD -> {
                                        priorityQueue.add(currWork.value)
                                    }
                                    POLL -> {
                                        currWork.value = priorityQueue.poll()
                                    }
                                    PEEK -> {
                                        currWork.value = priorityQueue.peek()
                                    }
                                }
                                currWork.op = DONE
                            }
                        }
                        lock.unlock()
                    }
//                        println(work.op)
                    if (work.op == DONE) {
                        works[randomIndex].value = null
                        return
                    }
                }
            }
        }
    }

    class Work<E>(var op: Int, var value: E? = null)
}
