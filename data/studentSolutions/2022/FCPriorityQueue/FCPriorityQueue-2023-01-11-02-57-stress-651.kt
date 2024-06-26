import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = AtomicBoolean(false)
    private val array = atomicArrayOfNulls<Cell<E>>(1024)
    private val random = ThreadLocalRandom.current()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return execute(Action.POLL, null) as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return execute(Action.PEEK, null) as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        execute(Action.ADD, element)
    }

    private fun execute(action: Action, element: E?): Any? {
        var myCell: Int? = null
        while (true) {
            if (this.lock.compareAndSet(false, true)) {
                var result: E? = null
                if (myCell != null) {
                    array[myCell].compareAndSet(array[myCell].value, null)
                }
                try {
                    when(action) {
                        Action.POLL -> result = q.poll()
                        Action.PEEK -> result = q.peek()
                        else -> q.add(element)
                    }
                    for (i in 0 .. 1023) {
                        val cellValue = array[i].value
                        if (cellValue != null && cellValue.result == null) {
                            when (cellValue.action) {
                                Action.POLL -> array[i].compareAndSet(cellValue, Cell(cellValue.action, q.poll()))
                                Action.PEEK -> array[i].compareAndSet(cellValue, Cell(cellValue.action, q.peek()))
                                else -> cellValue.result = array[i].compareAndSet(cellValue, Cell(cellValue.action, q.add(element)))
                            }
                        }
                    }
                    return result
                } finally {
                    this.lock.compareAndSet(true, false)
                }
            } else {
                if (myCell == null) {
                    val randomIndex = random.nextInt(1024)
                    val cellValue = array[randomIndex]
                    if (cellValue.value == null) {
                        if (cellValue.compareAndSet(null, Cell(action, null))) {
                            myCell = randomIndex
                        }
                    }
                } else {
                    val result = array[myCell].value
                    if (result!!.result != null) {
                        if (array[myCell].compareAndSet(result, null)) {
                            return result
                        }
                    }
                }
            }
        }
    }

    enum class Action {
        POLL,
        PEEK,
        ADD
    }

    class Cell<E>(val action: Action, var result: Any?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Cell<*>

            if (action != other.action) return false
            if (result != other.result) return false

            return true
        }

        override fun hashCode(): Int {
            var result1 = action.hashCode()
            result1 = 31 * result1 + (result?.hashCode() ?: 0)
            return result1
        }
    }
}