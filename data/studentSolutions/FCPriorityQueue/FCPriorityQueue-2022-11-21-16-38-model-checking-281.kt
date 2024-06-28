import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()

    private val fcArray = atomicArrayOfNulls<Operation>(20)

    private val lock = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (!lock.compareAndSet(expect = false, update = true)) {
            var place = ThreadLocalRandom.current().nextInt(fcArray.size)
            val operation = OperationPoll()
            val result = operation.result
            if (fcArray[place].compareAndSet(null, operation)) {
                while (true) {
                    if (fcArray[place].compareAndSet(result, null)) {
                        return result.result as E?
                    }
                    if (lock.compareAndSet(expect = false, update = true)) {
                        assert(fcArray[place].value == operation)
                        fcArray[place].value = null
                        val elem = q.poll()
                        combine()
                        lock.value = false
                        return elem
                    }
                }
            }
        } else {
            val elem = q.poll()
            combine()
            lock.value = false
            return elem
        }
        throw java.lang.IllegalStateException("cant be")
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (!lock.compareAndSet(expect = false, update = true)) {
            val place = ThreadLocalRandom.current().nextInt(fcArray.size)
            val operation = OperationAdd(element)
            val result = operation.result
            if (fcArray[place].compareAndSet(null, operation)) {
                while (true) {
                    if (fcArray[place].compareAndSet(result, null)) {
                        return
                    }
                    if (lock.compareAndSet(expect = false, update = true)) {
                        assert(fcArray[place].value == operation)
                        fcArray[place].value = null
                        q.offer(element)
                        combine()
                        lock.value = false
                        return
                    }
                }
            }
        } else {
            q.offer(element)
            combine()
            lock.value = false
            return
        }
        throw java.lang.IllegalStateException("cant be")
    }

    private fun combine() {
        for (i in 0 until fcArray.size) {
            val operation = fcArray[i].value
            if (operation == null) {

            } else if (operation is OperationAdd) {
                q.add(operation.e as E)
                fcArray[i].value = operation.result
            } else if (operation is OperationPoll) {
                operation.result.result = q.poll()
                fcArray[i].value = operation.result
            }
        }
    }

    interface Operation
    class OperationAdd(val e: Any, val result: Result = Result()) : Operation

    class OperationPoll(val result: Result = Result()) : Operation

    class Result(var result: Any? = null) : Operation
}