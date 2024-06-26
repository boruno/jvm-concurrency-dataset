import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

sealed interface Operation
object ADD : Operation
object STARTED : Operation
object POLL : Operation
object FINISHED : Operation
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic<Boolean>(false)
    private val size = Runtime.getRuntime().availableProcessors()
    private val array = atomicArrayOfNulls<Pair<Operation, E?>>(size)


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = start(Pair(POLL, null))

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = q.peek()

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        start(Pair(ADD, element))
    }


    private fun start(op: Pair<Operation, E?>): E? {
        var curPosition = -1
        while (curPosition != size) {
            if (curPosition == -1) {
                while (curPosition < size - 1) {
                    ++curPosition
                    if (array[curPosition].compareAndSet(null, op)) {
                        break
                    }
                }
                continue
            }
            if (!lock.compareAndSet(expect = false, update = true)) {
                continue
            }
            for (index in 0 until array.size) {
                val curOp = array[index]
                var value = curOp.value
                if (value == null || (value.first != ADD && value.first != POLL)) {
                    continue
                }
                if (array[index].compareAndSet(value, Pair(STARTED, value.second))) {
                    when (value.first) {
                        POLL -> value = Pair(POLL, q.poll())
                        ADD -> q.add(value.second)
                        else -> {}
                    }
                    array[index].getAndSet(Pair(FINISHED, value.second))
                }
            }
            lock.getAndSet(false)
            val curVal = array[curPosition].value
            if (curVal != null && curVal.first == FINISHED) {
                array[curPosition].compareAndSet(curVal, null)
                val ans = curVal.second
                return ans
            }
        }
        return null
    }
}