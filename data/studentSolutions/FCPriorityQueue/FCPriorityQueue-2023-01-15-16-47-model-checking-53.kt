import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*
import java.util.concurrent.ThreadLocalRandom

sealed class Operation<E : Comparable<E>>(val actionWithQueue: (PriorityQueue<E>) -> E?) {
    var result: E? = null
        private set
    var complete = false

    fun execute(queue: PriorityQueue<E>) {
        result = actionWithQueue(queue)
        complete = true
    }
}

class Add<E : Comparable<E>>(val element: E) : Operation<E>({ it.add(element); null })
class Poll<E : Comparable<E>>() : Operation<E>({ it.poll() })
class Peek<E : Comparable<E>>() : Operation<E>({ it.peek() })



class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()

    private val operations = atomicArrayOfNulls<Operation<E>>(Runtime.getRuntime().availableProcessors())

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val op = Poll<E>()
        run(op)
        return op.result
    }

    fun run(operation: Operation<E>) {
        val idx = addToArray(operation)
        while (true) {
            if (lock.tryLock()) {
                for (i in 0 until operations.size) {
                    val op = operations[i].value ?: continue
                    op.execute(q)
                    operations[i].value = null
                }
                lock.unlock()
            }
            if (operation.complete) {
                return
            }
        }
    }

    fun addToArray(operation: Operation<E>): Int {
        while (true) {
            val idx = ThreadLocalRandom.current().nextInt(operations.size)
            if (operations[idx].compareAndSet(null, operation)) {
                return idx
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val op = Peek<E>()
        run(op)
        return op.result
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val op = Add(element)
        run(op)
    }
}