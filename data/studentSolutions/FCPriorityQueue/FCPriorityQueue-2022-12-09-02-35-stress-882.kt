import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.abs

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val n = 15
    private val lock = ReentrantLock()
    private val nodes = atomicArrayOfNulls<Node<E>>(n)

    fun nodesProcess() {
        for (i in 0 until n) {
            if (nodes[i].value == null) continue
            val action = nodes[i].value!!
            when (action.type) {
                Type.POLL -> {
                    val lockedNode: Node<E> = Node(Type.LOCKED, null)
                    if (nodes[i].compareAndSet(action, lockedNode)) nodes[i].compareAndSet(
                        lockedNode,
                        Node(Type.REMOVED, q.poll())
                    )
                }

                Type.PEAK -> {
                    val lockedNode: Node<E> = Node(Type.LOCKED, null)
                    if (nodes[i].compareAndSet(action, lockedNode)) nodes[i].compareAndSet(
                        lockedNode,
                        Node(Type.REMOVED, q.peek())
                    )
                }

                Type.ADD -> {
                    val lockedNode: Node<E> = Node(Type.LOCKED, null)
                    if (nodes[i].compareAndSet(action, lockedNode)) {
                        q.add(action.element)
                        nodes[i].compareAndSet(lockedNode, Node(Type.REMOVED, null))
                    }
                }

                else -> {
                    assert(false)
                }
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            if (lock.tryLock()) {
                val value = q.poll()
                nodesProcess()
                lock.unlock()
                return value
            }
            val index = abs(Random().nextInt(0, n))
            if (nodes[index].compareAndSet(null, Node(Type.POLL, null))) {
                while (true) {
                    when (nodes[index].value!!.type) {
                        Type.POLL -> if (!lock.isLocked && nodes[index].compareAndSet(nodes[index].value, null)) break
                        Type.REMOVED -> if (nodes[index].compareAndSet(nodes[index].value, null))
                            return nodes[index].value!!.element

                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            if (lock.tryLock()) {
                val value = q.peek()
                nodesProcess()
                lock.unlock()
                return value
            }
            val index = abs(Random().nextInt(0, n))
            if (nodes[index].compareAndSet(null, Node(Type.PEAK, null))) {
                while (true) {
                    when (nodes[index].value!!.type) {
                        Type.PEAK -> if (!lock.isLocked && nodes[index].compareAndSet(nodes[index].value, null)) break
                        Type.REMOVED -> if (nodes[index].compareAndSet(nodes[index].value, null))
                            return nodes[index].value!!.element

                        else -> {}
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            if (lock.tryLock()) {
                q.add(element)
                nodesProcess()
                lock.unlock()
                return
            }
            val index = abs(Random().nextInt(0, n))
            if (nodes[index].compareAndSet(null, Node(Type.ADD, null))) {
                while (true) {
                    when (nodes[index].value!!.type) {
                        Type.ADD -> if (!lock.isLocked && nodes[index].compareAndSet(nodes[index].value, null)) break
                        Type.REMOVED -> if (nodes[index].compareAndSet(nodes[index].value, null))
                            return

                        else -> {}
                    }
                }
            }
        }
    }

    enum class Type {
        POLL, PEAK, ADD, LOCKED, REMOVED
    }

    data class Node<E>(val type: Type, val element: E?)
}
