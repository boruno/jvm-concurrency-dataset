import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference


class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val workers = Runtime.getRuntime().availableProcessors()
    private val operations = atomicArrayOfNulls<Operation<E?>>(20 * workers)
    private val lock = atomic(false)

    class Operation<E>(operation: String, value: E) {
        val type = operation
        val value = AtomicReference(value)
        val done = AtomicBoolean(false)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val operation = Operation<E?>(POLL, null)
        apply(operation)
        return operation.value.get()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val operation = Operation<E?>(PEEK, null)
        apply(operation)
        return operation.value.get()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val operation = Operation<E?>(ADD, element)
        apply(operation)
    }

    private fun applyFunc(operation: Operation<E?>) {
        if (operation.done.get())
            return

        when (operation.type) {
            ADD -> q.add(operation.value.get())
            PEEK -> operation.value.set(q.peek())
            POLL -> operation.value.set(q.poll())
        }

        operation.done.set(true)
    }

    private fun nextInt(): Int {
        return ThreadLocalRandom.current().nextInt(20 * workers)
    }

    private fun apply(operation: Operation<E?>) {
        var localId: Int
        var value: Operation<E?>
        while (true) {
            localId = nextInt()

            if (operations[localId].compareAndSet(null, operation)) {
                while (true) {
                    if (lock.compareAndSet(expect = false, update = true)) {
                        applyFunc(operation)

                        for (i in 0 until workers) {
                            value = operations[i].value!!
                            applyFunc(value)
                        }

                        lock.value = false
                    }

                    if (operation.done.get()) {
                        operations[localId].value = null
                        return
                    }
                }
            }
        }
    }

    companion object {
        const val POLL = "POLL"
        const val PEEK = "PEEK"
        const val ADD = "ADD"
    }
}