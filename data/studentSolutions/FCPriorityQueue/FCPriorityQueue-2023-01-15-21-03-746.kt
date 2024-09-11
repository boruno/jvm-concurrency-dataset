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
    private val size = 4 * Runtime.getRuntime().availableProcessors()
    private val array = atomicArrayOfNulls<Pair<Operation, E?>>(size)


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? = addOp(Pair(POLL, null))

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? = q.peek()

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        addOp(Pair(ADD, element))
    }


    private fun addOp(op: Pair<Operation, E?>): E? {
        var curPosition = -1
        var result = array[0].value!!
        while (result.first != FINISHED) {
            if (curPosition == -1) {
                while (curPosition < size - 1) {
                    ++curPosition
                    if (array[curPosition].compareAndSet(null, op)) {
                        break
                    }
                }
                continue
            }
            if (!lock.value && lock.compareAndSet(false, true)) {
                for (index in 0 until array.size) {
                    val curOp = array[index]
                    var value = curOp.value ?: continue
                    if (value.first != ADD && value.first != POLL) continue
                    if (array[index].compareAndSet(value, Pair(STARTED, value.second))) {
                        if (value.first == POLL) value = Pair(POLL, q.poll())
                        else q.add(value.second)
                        array[index].getAndSet(Pair(FINISHED, value.second))
                    }
                }
                lock.getAndSet(false)
            }
            result = array[curPosition].value!!
        }
        array[curPosition].compareAndSet(result, null)
        return result.second
    }
}