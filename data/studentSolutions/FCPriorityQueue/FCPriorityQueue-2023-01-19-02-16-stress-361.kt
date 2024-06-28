import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import java.util.*

@Suppress("UNCHECKED_CAST")
class FCPriorityQueue<E : Comparable<E>> {
    private val queue = PriorityQueue<E>()
    private val index = ThreadLocal.withInitial { getNext() }

    private fun getNext(): Int {
        while (true) {
            val x = threadCnt.value
            if (threadCnt.compareAndSet(x, x + 1))
                return x % THREAD_NUMBER
        }
    }

    private val arr = arrayOfNulls<Node<E>>(THREAD_NUMBER)
    private val lock: AtomicBoolean = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val curID = index.get()
        arr[curID] = Node(OperationType.POLL, null)
        while (true) {
            arr[curID] ?: break
            if (arr[curID]!!.op != OperationType.POLL)
                break
            if (!lock.compareAndSet(false, true)) continue
            for (i in arr.indices) {
                arr[i] ?: continue
                when (arr[i]!!.op) {
                    OperationType.POLL -> {
                        arr[i] = Node(OperationType.EMPTY, queue.poll())
                    }
                    else -> continue
                }
            }
            lock.getAndSet(false)
        }
        arr[curID] ?: return null
        return arr[curID]!!.element
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val curID = index.get()
        arr[curID] = Node(OperationType.PEEK, null)
        while (true) {
            arr[curID] ?: break
            if (arr[curID]!!.op != OperationType.PEEK)
                break
            if (!lock.compareAndSet(false, true)) continue
            for (i in arr.indices) {
                arr[i] ?: continue
                when (arr[i]!!.op) {
                    OperationType.PEEK -> arr[i] = Node(OperationType.EMPTY, queue.peek())
                    else -> continue
                }
            }
            lock.getAndSet(false)
        }
        arr[curID] ?: return null
        return arr[curID]!!.element
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val curID = index.get()
        if (arr[curID] == null || arr[curID]!!.op != OperationType.ADD) {
            arr[curID] = Node(OperationType.ADD, element)
        }
        while (true) {
            arr[curID] ?: break
            if (arr[curID]!!.op != OperationType.ADD)
                break
            if (!lock.compareAndSet(false, true)) continue
            for (i in arr.indices) {
                arr[i] ?: continue
                when (arr[i]!!.op) {
                    OperationType.ADD -> {
                        queue.add(element)
                        arr[i] = Node(OperationType.EMPTY, null)
                    //{el:E -> queue.add(el)}
                    }
                    else -> continue
                }
            }
            lock.getAndSet(false)
        }

    }

    /*private fun getAction(op: OperationType, element: E?): () -> E? {
        return when (op) {
            OperationType.POLL -> queue.poll()
            OperationType.ADD -> queue.add(element)
            else -> queue.peek()
        }
    }*/

}

private enum class OperationType {POLL, PEEK, ADD, EMPTY}
private class Node<E>(val op: OperationType, val element: E?)

private val threadCnt = atomic(0)
private const val THREAD_NUMBER = 3