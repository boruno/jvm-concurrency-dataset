import java.util.*
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls

class FCPriorityQueue<E : Comparable<E>> {
    private val workers = Runtime.getRuntime().availableProcessors()
    private val fcArray = FCArray<E>(4 * workers)
    private val q = PriorityQueue<E>()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return fcArray.waitTaskResult(Task(q, Action.Poll))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return fcArray.waitTaskResult(Task(q, Action.Peek))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        fcArray.waitTaskResult(Task(q, Action.Add, value = element))
    }

    private class FCArray<E>(val size : Int) {
        private val fcArray = atomicArrayOfNulls<Task<E>>(size)
        private var random = Random()
        private val lock = atomic(false)

        fun waitTaskResult(task : Task<E>) : E? {
            val ind : Int
            while (true) {
                val newInd = random.nextInt(size)
                if (fcArray[newInd].compareAndSet(null, task)) {
                    ind = newInd
                    break
                }
            }
            while (true) {
                if (fcArray[ind].value!!.finished) {
                    val result = fcArray[ind].value!!.value
                    fcArray[ind].value = null
                    return result
                }
                if (!lock.value) {
                    if (tryLock()) {
                        fcArray[ind].value = null
                        val res = task.process()
                        processAll()
                        unlock()
                        return res
                    }
                }
            }
        }

        fun processAll() {
            for (i in 0 until size){
                val curTask = fcArray[i].value
                if (curTask !== null) {
                    curTask.process()
                }

            }
        }

        fun tryLock() : Boolean {
            return lock.compareAndSet(expect = false, update = true)
        }

        fun unlock() {
            lock.value = false
        }

    }

    enum class Action {
        Poll, Peek, Add
    }
    private class Task<E> (val q : PriorityQueue<E>, val action : Action, var value : E? = null) {
        var finished = false

        fun process() : E? {
            when (action) {
                Action.Peek -> {
                    value = q.firstOrNull()
                    finished = true
                }
                Action.Poll -> {
                    value = q.firstOrNull()
                    q.poll()
                    finished = true
                }
                Action.Add -> {
                    q.add(value)
                    finished = true
                }
            }
            return value
        }
    }
}