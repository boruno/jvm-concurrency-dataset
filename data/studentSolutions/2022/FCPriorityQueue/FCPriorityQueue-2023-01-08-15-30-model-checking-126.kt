import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

sealed interface Element {}
sealed interface Operation: Element {
    object Poll : Operation
    object Peek : Operation
    data class Add<E>(val element: E) : Operation
}

sealed interface Result: Element {
    data class ResultElement<E>(val element: E): Result
}

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val isQueueLocked = atomic(false)
    private val fcArray = atomicArrayOfNulls<Element>(ARRAY_SIZE)

    fun becomeCombinerOrWait(operation: Operation) : E? {
        var i: Int
        while (true) {
            i = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
            if (fcArray[i].compareAndSet(null, operation)) { break }
        }

        while (true) {
            if (fcArray[i].value is Result) { break }

            if (isQueueLocked.compareAndSet(false, true)) {
                for (i in 0 until ARRAY_SIZE) {
                    val element = fcArray[i].value
                    if (element != null && element is Operation) {
                        when (element) {
                            Operation.Poll ->  {
                                fcArray[i].compareAndSet(element, Result.ResultElement(q.poll()))
                            }
                            Operation.Peek -> {
                                fcArray[i].compareAndSet(element, Result.ResultElement(q.peek()))
                            }
                            is Operation.Add<*> -> {
                                if (fcArray[i].compareAndSet(element, Result.ResultElement(null))) {
                                    q.add(element.element as E)
                                }
                            }
                        }
                    }
                }
                isQueueLocked.compareAndSet(true, false)
            }
        }

        val result = fcArray[i].value as? Result.ResultElement<*>

        return result?.element as? E
    }
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return becomeCombinerOrWait(Operation.Poll)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return becomeCombinerOrWait(Operation.Peek)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        becomeCombinerOrWait(Operation.Add(element))
    }
}

private const val ARRAY_SIZE = 30