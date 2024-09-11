import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
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

    private val arr = arrayOfNulls<Any>(THREAD_NUMBER)
    private val ans = arrayOfNulls<Node<E>>(THREAD_NUMBER)
    private val lock: AtomicBoolean = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return abstractLockOperation { queue.poll() }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return abstractLockOperation { queue.peek() }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        abstractLockOperation {
            queue.add(element)
            null
        }
    }

    private fun abstractLockOperation(operation: () -> E?): E? {
        val curID = index.get()
        arr[curID] = operation
        while (true) {
            if (arr[curID] != operation)
                break
            if (lock.value) continue
            if (!lock.compareAndSet(false, true)) continue
            for (i in arr.indices) {
                val element = arr[i]
                if (element !is Function0<*>) continue
                ans[i] = Node(element() as E?)
            }
            unlock()
        }
        ans[curID] ?: return null
        return ans[curID]!!.element //as E?
    }

    private fun unlock() {
        lock.value = false
    }

    private class Node<E>(val element: E?)
}

private val threadCnt = atomic(0)
private const val THREAD_NUMBER = 3