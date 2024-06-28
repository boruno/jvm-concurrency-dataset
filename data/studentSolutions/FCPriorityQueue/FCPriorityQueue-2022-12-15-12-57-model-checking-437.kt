import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val fcArray = atomicArrayOfNulls<Node<E>>(50)

    class Node<E>(var operation: Operations, var element: E?)

    enum class Operations {
        ADD,
        POLL,
        PEEK,
        DONE
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return combine(Node(Operations.POLL, null))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return combine(Node(Operations.PEEK, null))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        combine(Node(Operations.ADD, element))
    }

    private fun putNode(node: Node<E>) {
        var idx = Random().nextInt(0, fcArray.size)
        while (true) {
            if (fcArray[idx].compareAndSet(null, node)) {
                return
            }
            idx = (idx + 1) % fcArray.size
        }
    }

    private fun waitOrRun(node: Node<E>) {
        while (true) {
            if (lock.compareAndSet(false, true)) {
                for (i in 0 until fcArray.size) {
                    val value = fcArray[i].value
                    if (value != null) {
                        when (value.operation) {
                            Operations.ADD -> q.add(value.element)
                            Operations.POLL -> value.element = q.poll()
                            Operations.PEEK -> value.element = q.peek()
                            else -> {}
                        }
                        value.operation = Operations.DONE
                    }
                }
                lock.compareAndSet(true, false)
                break
            }
            if (node.operation == Operations.DONE) {
                break
            }
        }
    }

    private fun combine(node: Node<E>): E? {
        putNode(node)
        waitOrRun(node)
        return node.element
    }
}