import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val ThreadsCount: Int
    private val a: AtomicReferenceArray<Any?>
    private val lock = Lock(AtomicReference(false))

    init {
        ThreadsCount = Runtime.getRuntime().availableProcessors() * 2
        a = AtomicReferenceArray(ThreadsCount)
        for (i in 0 until ThreadsCount) {
            a.set(i, null)
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return commitOperation(CustomOperation { q.poll() })
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return commitOperation(CustomOperation { q.peek() })
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        commitOperation(CustomOperation { q.add(element) })
    }

    private fun commitOperation(operation: CustomOperation): E? {
        val index = placeOperation(operation)
        if (index == -1) {
            asCombiner()
            val result = combinerOperation(operation)
            lock.unlock()
            return result
        }

        while (true) {
            val result = a[index]
            if (result !is CustomOperation) {
                a.compareAndSet(index, result, null)
                return result as E?
            }

            if (lock.lock()) {
                asCombiner()
                lock.unlock()
            }
        }
    }

    private fun placeOperation(operation: CustomOperation): Int {
        while (true) {
            if (lock.lock()) {
                return -1
            }

            val index = Random.nextInt(ThreadsCount)
            if (a.compareAndSet(index, null, operation)) {
                return index
            }
        }
    }

    private fun asCombiner() {
        for (i in 0 until ThreadsCount) {
            val operation = a[i]
            if (operation !is CustomOperation) {
                continue
            }

            a.set(i, combinerOperation(operation))
        }
    }

    private fun combinerOperation(operation: CustomOperation): E? {
        return operation.op() as E?
    }
//        when (operation) {
//            is PollOperation -> q.poll()
//            is PeekOperation -> q.peek()
//            is AddOperation -> {
//                q.add(operation.x as E)
//                null
//            }
//
//            else -> {
//                throw Exception()
//            }
//        }
}

private val FREE = Any()

class CustomOperation(val op: () -> Any?)

interface Operation

class PollOperation : Operation {
    companion object {
        val INSTANCE = PollOperation()
    }
}

class PeekOperation : Operation {
    companion object {
        val INSTANCE = PeekOperation()
    }
}

class AddOperation(val x: Any) : Operation

class Lock(val lock: AtomicReference<Boolean>) {
    fun lock(): Boolean {
        return lock.compareAndSet(false, true)
    }

    fun unlock() {
        lock.set(false)
    }
}