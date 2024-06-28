import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val operationsArraySize = Runtime.getRuntime().availableProcessors()
    private val operations = atomicArrayOfNulls<QueueOperation<E>>(operationsArraySize)

    private fun locked(): Boolean {
        return lock.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        lock.getAndSet(false)
    }
    private fun operate(operation: () -> E?): E? {
        val executable = QueueOperation(operation)
        var randomIndex = ThreadLocalRandom.current().nextInt(operationsArraySize)
        for (i in 0 until operationsArraySize) {
            if (operations[randomIndex].compareAndSet(null, executable)) {
                break
            }
            randomIndex++
            randomIndex %= operationsArraySize
        }
        while (true) {
            if (locked()) {
                try {
                    var index = 0
                    repeat(operationsArraySize) {
                        val queueOperation = operations[index++].value
                        if (queueOperation != null && queueOperation.waiting) {
                            queueOperation.execute()
                        }
                    }
                } finally {
                    unlock()
                }
                return (operations[randomIndex].getAndSet(null))?.res
            }
        }
    }
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return operate { q.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return operate { q.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        operate { q.add(element); null }
    }
}

private class QueueOperation<E>(val operation: () -> E?) {
    var waiting = true
    var res: E? = null
    fun execute() {
        waiting = false
        res = operation()
    }
}