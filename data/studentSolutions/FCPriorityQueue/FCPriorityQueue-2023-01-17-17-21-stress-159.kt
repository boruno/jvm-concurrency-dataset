import java.util.PriorityQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicReference

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray: Array<AtomicReference<Operation<E>?>> = Array(FC_ARRAY_SIZE) { AtomicReference(null) }
    private var isLocked: AtomicReference<Boolean> = AtomicReference(false)
    private val generator: ThreadLocalRandom = ThreadLocalRandom.current()
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (tryLock()) {
            return pollLocked()
        }

       return checkProcessed(Operation(null, OperationType.POLL)) ?: pollLocked()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (tryLock()) {
            return peekLocked()
        }

        return checkProcessed(Operation(null, OperationType.PEEK)) ?: peekLocked()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        if (tryLock())
            return addLocked(element)

        if (checkProcessed(Operation(element, OperationType.ADD)) == null)
            addLocked(element)
    }

    private fun pollLocked(): E?{
        val res = q.poll()
        help()
        unlock()
        return res
    }
    private fun peekLocked(): E? {
        val res = q.peek()
        help()
        unlock()
        return res
    }
    private fun addLocked(element: E) {
        q.add(element)
        help()
        unlock()
    }

    private fun tryLock() = isLocked.compareAndSet(false, true)
    private fun unlock() = isLocked.set(false)

    private fun help() {
        for (i in (0 until FC_ARRAY_SIZE)) {
            if (fcArray[i].get() == null)
                continue
            val fci = fcArray[i].get()!!
            if (fci.type == OperationType.POLL) {
                fcArray[i].set(Operation(q.poll(), OperationType.RESULT))
            } else if(fci.type == OperationType.PEEK) {
                fcArray[i].set(Operation(q.peek(), OperationType.RESULT))
            } else if(fci.type == OperationType.ADD) {
                q.add(fci.value)
                fcArray[i].set(Operation(fci.value, OperationType.RESULT))
            }
        }
    }

    private fun generateIndex() = generator.nextInt(FC_ARRAY_SIZE)
    private fun bookFC(operation: Operation<E>): Int {
        var index = generateIndex()
        while (!fcArray[index].compareAndSet(null, operation))
            index = generateIndex()

        return index
    }
    private fun checkProcessed(operation: Operation<E>): E? {
        val index = bookFC(operation)

        while (!tryLock()) {
            val fc = fcArray[index].get()!!
            if (fc.type == OperationType.RESULT) {
                fcArray[index].set(null)
                return fc.value
            }
        }

        val fc = fcArray[index].get()!!
        if (fc.type == OperationType.RESULT) {
            fcArray[index].set(null)
            unlock()
            return fc.value
        }

        fcArray[index].set(null)
        return null
    }
}

const val FC_ARRAY_SIZE = 6

private class Operation<E>(val value: E?, val type: OperationType)

private enum class OperationType{
    POLL,
    PEEK,
    ADD,
    RESULT
}
