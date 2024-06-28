import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*


class FCPriorityQueue<E : Comparable<E>> {
    val lock = atomic(false)
    private val random = Random()
    private val n = 4 * Runtime.getRuntime().availableProcessors()
    private val actionAtomicArray = atomicArrayOfNulls<Action<E>?>(n)
    private val q = PriorityQueue<E>()

    fun tryLock(): Boolean {
        if (lock.compareAndSet(false, true)) {
            return lock.value
        } else return false
    }

    fun unlock() {
        lock.compareAndSet(true, false)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = operation(Action("poll", null), f("poll")) as E

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = operation(Action("peek", null), f("peek")) as E

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) = operation(Action("add", element), f("add"))

    private val retrieveElement: (Int, Action<E>) -> E? = { idx: Int, worker: Action<E> ->
        val res = worker.arg
        actionAtomicArray[idx].value = null
        res
    }

    fun f(a : String): (Int, Action<E>) -> Unit {
        if (a == "add") {
            return  { idx, _ -> actionAtomicArray[idx].value = null }
        } else {
            return { idx: Int, act: Action<E> ->
                val res = act.arg
                actionAtomicArray[idx].value = null
                res
            }
        }
    }

    private fun <X> operation(action: Action<E>, ret: (Int, Action<E>) -> X): X {
        while (true) {
            val idx = random.nextInt(n)
            if (actionAtomicArray[idx].compareAndSet(null, action)) {
                if (actionAtomicArray[idx].value != action) {
                    throw IllegalArgumentException()
                }
                while (true) {
                    if (tryLock()) {
                        for (i in 0 until n) {
                            val currentOperation = actionAtomicArray[i].value
                            if (currentOperation == null) {
                                continue
                            } else if (currentOperation.name == "add") {
                                q.add(currentOperation.arg)
                            } else if (currentOperation.name == "poll") {
                                currentOperation.arg = q.poll()
                            } else if (currentOperation.name == "peek")  {
                                currentOperation.arg = q.peek()
                            }
                            currentOperation.name = "done"
                        }
                        unlock()
                    }
                    if (action.name == "done") {
                        return ret(idx, action)
                    }
                }
            }
        }
    }

    data class Action<E>(var name: String, var arg: E?)
}
