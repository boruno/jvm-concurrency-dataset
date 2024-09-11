import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val locked = atomic(false)
    private val fcArray = atomicArrayOfNulls<FlatCombineInfo<E>>(FC_ARRAY_SIZE)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val fcInfo = FlatCombineInfo<E>(Operation.POLL, null)
        flatCombine(fcInfo)
        return fcInfo.element
    }

    private fun flatCombine(fcInfo: FlatCombineInfo<E>) {
        var i = 0
        while (true) {
            if (fcArray[i].compareAndSet(null, fcInfo)) {
                break
            }
            i = (i + 1) % FC_ARRAY_SIZE
        }
        while (fcArray[i].value != null) {
            if (locked.compareAndSet(expect = false, update = true)) {
                combine()
                locked.value = false
                break
            }
        }
    }

    private fun combine() {
        for (i in 0..fcArray.size) {
            val fcInfo = fcArray[i].value
            if (fcInfo != null) {
                when (fcInfo.operation) {
                    Operation.POLL -> {
                        fcInfo.element = q.poll()
                    }

                    Operation.PEEK -> {
                        fcInfo.element = q.peek()
                    }

                    else -> {
                        q.add(fcInfo.element)
                    }
                }
                fcArray[i].value = null
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val fcInfo = FlatCombineInfo<E>(Operation.PEEK, null)
        flatCombine(fcInfo)
        return fcInfo.element
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val fcInfo = FlatCombineInfo(Operation.ADD, element)
        flatCombine(fcInfo)
    }
}

private const val FC_ARRAY_SIZE = 3

private enum class Operation {
    POLL, PEEK, ADD
}

private class FlatCombineInfo<E>(val operation: Operation, var element: E?)