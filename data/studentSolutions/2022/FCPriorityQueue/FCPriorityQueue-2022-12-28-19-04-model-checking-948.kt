import Operation.*
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import java.lang.Integer.max
import java.lang.Math.min
import java.util.*
import java.util.concurrent.locks.ReentrantLock

enum class Operation {
    PEEK,
    POLL,
    ADD,
    DONE,
}
data class Node<E> (
    val op: Operation,
    val x: E? = null,
)
class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = reentrantLock()
    private val nodes = atomicArrayOfNulls<Node<E>>(10)


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */

    private fun combine() {
        for (nodeIndex in 0 until nodes.size) {
            val node = nodes[nodeIndex].value ?: continue
            when (node.op) {
                PEEK -> {
                    val ans = q.peek()
                    nodes[nodeIndex].compareAndSet(node, Node(DONE, ans))
                }

                POLL -> {
                    val ans = q.peek()
                    if (nodes[nodeIndex].compareAndSet(node, Node(DONE, ans))) q.poll()
                }

                ADD -> if (nodes[nodeIndex].compareAndSet(node, Node(DONE, null))) q.add(node.x)
                DONE -> continue
            }
        }
    }

    private fun commonMethod(node: Node<E>): Node<E>? {
        while (true) {
            val c = Random().nextInt(nodes.size)
            val a = max(c - 2, 0)
            val b = (c + 2).coerceAtMost(nodes.size - 1)
            for (i in a until  b + 1) {
                if (nodes[i].compareAndSet(null, node)) {
                    while (true) {
                        val newNode = nodes[i].value
                        if (newNode != null && newNode.op == DONE) {
                            nodes[i].compareAndSet(newNode, null)
                            return newNode
                        } else {
                            if (nodes[i].compareAndSet(newNode, null)) {
                                return null
                            }
                        }
                    }
                }
            }
        }
    }

    fun poll(): E? {
        while (true) {
            if (!lock.isLocked)  {
                lock.withLock {
                    combine()
                    return q.poll()
                }
            } else {
                return commonMethod(Node(POLL, null))?.x ?: continue
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            if (!lock.isLocked)  {
                lock.withLock {
                    combine()
                    return q.peek()
                }
            } else {
                return commonMethod(Node(PEEK, null))?.x ?: continue
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            if (!lock.isLocked)  {
                lock.withLock {
                    combine()
                    q.add(element)
                    return
                }
            } else {
                commonMethod(Node(ADD, null)) ?: continue
                return
            }
        }
    }
}