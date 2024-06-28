import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.function.Supplier

const val TASKS_SIZE = 4
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val tasks = atomicArrayOfNulls<Any>(TASKS_SIZE)

    private fun <R> doTask(action : () -> R): R {
        var selected = -1
        val asSup = Supplier<R> {
            val a = action()
            assert(a !== Unit)
            a
        }

        while (true) {
            if (lock.compareAndSet(false, true)) {
                try {
                    for (i in 0 until  tasks.size) {
                        val x = tasks[i].value
                        if (x is Supplier<*>) {
                            tasks[i].value = x.get()
                        }
                    }
                    if (selected == -1) {
                        return action()
                    }
                    val x = tasks[selected].value
                    tasks[selected].value = null
                    return if (x is Supplier<*>) {
                        val a = x.get()
                        assert(a !== Unit)
                        a as R
                    } else {
                        x as R
                    }
                } finally {
                    lock.value = false
                }
            } else {
                if (selected != -1) {
                    val x = tasks[selected].value
                    if (x !is Supplier<*>) {
                        tasks[selected].value = null
                        return x as R
                    }
                } else {
                    for (i in 0..32) {
                        val ind = ThreadLocalRandom.current().nextInt(0, tasks.size)
                        if (tasks[ind].compareAndSet(null, asSup)) {
                            selected = ind
                            break
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
    fun poll(): E? =
        doTask { q.poll() }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? =
        doTask { q.peek() }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E): Unit {
        doTask<Boolean> { q.add(element) }
    }
}