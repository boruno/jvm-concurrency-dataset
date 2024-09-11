import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*


private const val SIZE = 32
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Node<E>>(SIZE)
    private val lock = atomic(false)

    private enum class OP {
        ADD, POLL, END, PROGRESS
    }
    private class Node<E>(var element: E?, val operation: OP)

    private fun trav() {
        if (!lock.compareAndSet(expect = false, update = true)) return
        for (i in 0 until SIZE) {
            val operation = fcArray[i]
            val node = operation.value ?: continue
            if (node.operation == OP.POLL) {
                if (fcArray[i].compareAndSet(node, Node(node.element, OP.PROGRESS))) {
                    node.element = q.poll()
                    fcArray[i].getAndSet(Node(node.element, OP.END))
                }
            }
            if (node.operation == OP.ADD) {
                if (fcArray[i].compareAndSet(node, Node(node.element, OP.PROGRESS))) {
                    q.add(node.element)
                    fcArray[i].getAndSet(Node(node.element, OP.END))
                }
            }
        }
        lock.compareAndSet(expect = true, update = false)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var flag = -1
        while (true) {
            if (flag == -1) {
                for (pos in 0 until SIZE) {
                    if (fcArray[pos].compareAndSet(null, Node(null, OP.POLL))) {
                        flag = pos
                        break
                    }
                }
            } else {
                if (!lock.value) {
                    trav()
                }
                val res = fcArray[flag].value!!
                if (res.operation == OP.END) {
                    fcArray[flag].compareAndSet(res, null)
                    return res.element
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var flag = -1
        while (true) {
            if (flag == -1) {
                for (pos in 0 until SIZE) {
                    if (fcArray[pos].compareAndSet(null, Node(element, OP.ADD))) {
                        flag = pos
                        break
                    }
                }
            } else {
                if (!lock.value) {
                    trav()
                }
                val res = fcArray[flag].value!!
                if (res.operation == OP.END) {
                    fcArray[flag].compareAndSet(res, null)
                }
            }
        }
    }
}
