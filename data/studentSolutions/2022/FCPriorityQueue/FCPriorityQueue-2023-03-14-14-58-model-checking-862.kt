import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val array: Array<AtomicReference<Any?>>

    private val lock: AtomicReference<Boolean>
    private val Empty: Any
    private val ThreadsCount: Int

    init {
        lock = AtomicReference(false)
        Empty = Any()
        ThreadsCount = Runtime.getRuntime().availableProcessors() * 2
        array = Array(ThreadsCount) { AtomicReference(Empty) }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return commit(Operation { q.poll() })
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return commit(Operation { q.peek() })
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        commit(Operation {
            q.add(element)
            null
        })
    }

    private fun commit(operation: Operation): E? {
        var index = -1
        while (true) {
            if (lock.compareAndSet(false, true)) {
                break
            }

            index = Random.nextInt(ThreadsCount)
            if (array[index].compareAndSet(Empty, operation)) {
                break
            }
        }

        if (index == -1) {
            executeAll()
            val element = execute(operation)
            lock.set(false)
            return element
        }

        while (true) {
            val element = array[index].get()
            if (element !is Operation) {
                array[index].compareAndSet(element, Empty)
                return element as E?
            }

            if (lock.compareAndSet(false, true)) {
                executeAll()
                lock.set(false)
            }
        }
    }

    private fun executeAll() {
        for (i in 0 until ThreadsCount) {
            val element = array[i].get()
            if (element !is Operation) {
                continue
            }

            array[i].set(execute(element))
        }
    }

    private fun execute(operation: Operation): E? {
        return operation.op() as E?
    }
}

class Operation(val op: () -> Any?)