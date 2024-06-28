import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.sql.DriverManager.println
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
        isLocked.compareAndSet(true, false)
    }

    private fun help() {
        val j = SEGMENT_SIZE - 1
        for (i in 0..j) {
            val element = elements[i].value
            if (element == null) {
                continue
            }
            if (element.operation == "add") {
                q.add(elements[i].value!!.x)
                elements[i].getAndSet( null)
                continue
            }
            if (element.operation == "poll") {
                val value = q.poll()
                elements[i].getAndSet(Node(value, "done"))
                continue
            }
            if (element.operation == "peek") {
                val value = q.peek()
                elements[i].getAndSet(Node(value, "done"))
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
        var elimIndex = 0
        elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        while (!elements[elimIndex].compareAndSet(null, Node(null, "poll"))) {
            elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        }
        while (!tryToLock()) {
            if (elements[elimIndex].value!!.operation == "done") {
                val res = elements[elimIndex].value!!.x
                elements[elimIndex].getAndSet(null)
                return res
            }
        }
        if (elements[elimIndex].value!= null && elements[elimIndex].value!!.operation == "done") {
            val res = elements[elimIndex].value!!.x
            elements[elimIndex].getAndSet(null)
            unlock()
            return res
        }
        val value = q.poll()
        elements[elimIndex].getAndSet(null)
        help()
        return value
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var elimIndex = 0
        elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        while (!elements[elimIndex].compareAndSet(null, Node(null, "peek"))) {
            elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        }
        while (!tryToLock()) {
            if (elements[elimIndex].value!!.operation == "done") {
                val res = elements[elimIndex].value!!.x
                elements[elimIndex].getAndSet(null)
                return res
            }
        }
        if (elements[elimIndex].value!= null && elements[elimIndex].value!!.operation == "done") {
            val res = elements[elimIndex].value!!.x
            elements[elimIndex].getAndSet(null)
            unlock()
            return res
        }
        val value = q.peek()
        elements[elimIndex].getAndSet(null)
        help()
        return value
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var elimIndex = 0
        elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        while (!elements[elimIndex].compareAndSet(null, Node(element, "add"))) {
            elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
        }
        while (!tryToLock()) {
            if (elements[elimIndex].value == null) {
                return
            }
        }
        if (elements[elimIndex].value == null) {
            unlock()
            return
        }
        q.add(element)
        elements[elimIndex].getAndSet(null)
        help()
    }
}

private class Node<E>(val x: E?, val operation: String)

const val SEGMENT_SIZE = 2


//class FCPriorityQueue<E : Comparable<E>> {
//    private val q = PriorityQueue<E>()
//    private val elements = atomicArrayOfNulls<Node<E>>(SEGMENT_SIZE)
//    private val isLocked = AtomicBoolean(false)
//
//    private fun tryToLock(): Boolean {
//        return isLocked.compareAndSet(false, true)
//    }
//
//    private fun unlock() {
//        isLocked.compareAndSet(true, false)
//    }
//
//
//    /**
//     * Retrieves the element with the highest priority
//     * and returns it as the result of this function;
//     * returns `null` if the queue is empty.
//     */
//    fun poll(): E? {
//
//        q.poll()
//    }
//
//    /**
//     * Returns the element with the highest priority
//     * or `null` if the queue is empty.
//     */
//    fun peek(): E? {
//        var elimIndex = 0
//        elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
//        while (!elements[elimIndex].compareAndSet(null, Node(null, "peek"))) {
//            elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
//        }
//        while (!tryToLock()) {
//            if (elements[elimIndex].value!!.operation == "done") {
//                val res = elements[elimIndex].value!!.x
//                elements[elimIndex].getAndSet(null)
//                return res
//            }
//        }
//        if (elements[elimIndex].value!= null && elements[elimIndex].value!!.operation == "done") {
//            val res = elements[elimIndex].value!!.x
//            elements[elimIndex].getAndSet(null)
//            unlock()
//            return res
//        }
//        val value = q.peek()
//        elements[elimIndex].getAndSet(null)
//        help()
//        return value
//    }
//
//    /**
//     * Adds the specified element to the queue.
//     */
//    fun add(element: E) {
//        var elimIndex = 0
//        elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
//        while (!elements[elimIndex].compareAndSet(null, Node(element, "add"))) {
//            elimIndex = ThreadLocalRandom.current().nextInt(SEGMENT_SIZE)
//        }
//        while (!tryToLock()) {
//            if (elements[elimIndex].value == null) {
//                return
//            }
//        }
//        if (elements[elimIndex].value == null) {
//            unlock()
//            return
//        }
//        q.add(element)
//        elements[elimIndex].getAndSet(null)
//        help()
//    }
//}
//
//private class Node<E>(val x: E?, val operation: String)
//
//const val SEGMENT_SIZE = 2