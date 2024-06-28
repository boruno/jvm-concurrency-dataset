import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val elements = atomicArrayOfNulls<Node<E>>(SEGMENT_SIZE)
    private val isLocked = AtomicBoolean(false)

    private fun tryToLock(): Boolean {
        return isLocked.compareAndSet(false, true)
    }

    private fun unlock() {
        isLocked.set(false)
    }

    private fun help() {
        val j = SEGMENT_SIZE - 1
        for (i in 0..j) {
            if (elements[i].value == null) {
                continue
            }
            if (elements[i].value!!.operation == "add") {
                q.add(elements[i].value!!.x)
                elements[i].compareAndSet(elements[i].value, null)
                continue
            }
            if (elements[i].value!!.operation == "poll") {
                val value = q.poll()
                elements[i].compareAndSet(elements[i].value, Node(value, "done"))
                continue
            }
            if (elements[i].value!!.operation == "peek") {
                val value = q.peek()
                elements[i].compareAndSet(elements[i].value, Node(value, "done"))
                continue
            }
        }
        unlock()
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        var firstCall = true
        while (!tryToLock()) {
            if (firstCall) {
                firstCall = false
                elements[elimIndex].compareAndSet(null, Node(null, "poll"))
            } else {
                if (elements[elimIndex].value!!.operation == "done") {
                    return elements[elimIndex].value!!.x
                }
            }
        }
        val value = q.poll()
        help()
        return value
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        var firstCall = true
        while (!tryToLock()) {
            if (firstCall) {
                firstCall = false
                elements[elimIndex].compareAndSet(null, Node(null, "peek"))
            } else {
                if (elements[elimIndex].value!!.operation == "done") {
                    return elements[elimIndex].value!!.x
                }
            }
        }
        val value = q.peek()
        help()
        return value
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        var firstCall = true
        while (!tryToLock()) {
            if (firstCall) {
                firstCall = false
                elements[elimIndex].compareAndSet(null, Node(element, "add"))
            } else {
                if (elements[elimIndex].value == null) {
                    return
                }
            }
        }
        q.add(element)
        help()
    }
}

private class Node<E>(val x: E?, val operation: String)

const val SEGMENT_SIZE = 6