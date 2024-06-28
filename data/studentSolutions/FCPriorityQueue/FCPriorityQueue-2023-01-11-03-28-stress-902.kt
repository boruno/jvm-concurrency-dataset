import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = AtomicBoolean(false)
    private val array = atomicArrayOfNulls<Cell>(128)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return execute(Action.POLL) as E?
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return execute(Action.PEEK) as E?
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        execute(Action.ADD, element)
    }

    private fun execute(action: Action, element: E? = null): Any? {
        var myCell = Int.MAX_VALUE
        while (true) {
            if (this.lock.compareAndSet(false, true)) {
                var result: E? = null
                try {
                    if (myCell != Int.MAX_VALUE) {
                        val result_ = array[myCell].getAndSet(null)
                        if (result_?.result != null) return result_
                    }
                    for (i in 0 .. 127) {
                        val cellValue = array[i].value
                        if (cellValue != null) {
                            if (cellValue.result != null) {
                                continue
                            }
                            when (cellValue.action) {
                                Action.POLL -> cellValue.result = q.poll()
                                Action.PEEK -> cellValue.result = q.peek()
                                else -> cellValue.result = q.add(element)
                            }
                        }
                    }
                   return when(action) {
                        Action.POLL -> q.poll()
                        Action.PEEK -> q.peek()
                        else -> q.add(element)
                    }
                } finally {
                    this.lock.compareAndSet(true, false)
                }
            } else {
                if (myCell == Int.MAX_VALUE) {
                    for (i in 0 .. 127) {
                        val newValue = Cell(action, null)
                        if (array[i].compareAndSet(null, newValue)) {
                            myCell = i
                            break
                        }
                    }
                } else {
                    val result = array[myCell].value
                    if (result?.result != null) {
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

    class Cell(val action: Action, var result: Any?) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Cell

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