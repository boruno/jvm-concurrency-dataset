import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

sealed interface Operation {
    object Poll : Operation
    object Peek : Operation
    data class Add<E>(val element: E) : Operation
}

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val isQueueLocked = atomic(false)
    private val fcArray = atomicArrayOfNulls<Operation>(ARRAY_SIZE)

    fun becomeCombiner() {
//        when (operation) {
//            Operation.Poll -> q.poll()
//            Operation.Peek -> q.peek()
//            is Operation.Add<*> -> q.add(operation.element as E)
//        }

        for (i in 0 until ARRAY_SIZE) {
            val operation = fcArray[i].value
            if (operation != null) {
                fcArray[i].compareAndSet(operation, null)

//                when (operation) {
//                    Operation.Poll -> q.poll()
//                    Operation.Peek -> q.peek()
//                    is Operation.Add<*> -> q.add(operation.element as E)
//                }
            }
        }

        isQueueLocked.compareAndSet(true, false)
    }
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (isQueueLocked.compareAndSet(false, true)) {
            becomeCombiner()
            return q.poll()
        } else {
            val i = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
            while (true) {
                if (fcArray[i].compareAndSet(null, Operation.Poll)) { break }
            }

            while (true) {
                if (fcArray[i].value == null) { return q.poll() }

                if (isQueueLocked.compareAndSet(false, true)) {
                    becomeCombiner()
                    return q.poll()
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (isQueueLocked.compareAndSet(false, true)) {
            becomeCombiner()
            return q.peek()
        } else {
            val i = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
            while (true) {
                if (fcArray[i].compareAndSet(null, Operation.Poll)) { break }
            }

            while (true) {
                if (fcArray[i].value == null) { return q.peek() }

                if (isQueueLocked.compareAndSet(false, true)) {
                    becomeCombiner()
                    return q.peek()
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (isQueueLocked.compareAndSet(false, true)) {
            becomeCombiner()
            q.add(element)
        } else {
            val i = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
            while (true) {
                if (fcArray[i].compareAndSet(null, Operation.Poll)) { break }
            }

            while (true) {
                if (fcArray[i].value == null) { q.add(element) }

                if (isQueueLocked.compareAndSet(false, true)) {
                    becomeCombiner()
                    q.add(element)
                }
            }
        }
    }
}

private const val ARRAY_SIZE = 30