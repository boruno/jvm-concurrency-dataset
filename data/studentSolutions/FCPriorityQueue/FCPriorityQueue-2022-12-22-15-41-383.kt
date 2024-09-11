import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    class Node<E>(val type: Int, val element: E?)

    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val n = Random().nextInt(10, 20)
    private val nodes = atomicArrayOfNulls<Node<E>>(n)
    private val add = 0
    private val peek = 1
    private val poll = 2
    private val locked = 3
    private val removed = 4


    private fun listProcess() {
        for (index in 0 until n) {
            val cur = nodes[index].value ?: continue
            val lockedValue = Node<E>(locked, null)
            when (cur.type) {
                add -> {
                    if (nodes[index].compareAndSet(cur, lockedValue)) {
                        q.add(cur.element)
                        nodes[index].compareAndSet(lockedValue, Node(removed, null))
                    }
                }

                peek -> {
                    if (nodes[index].compareAndSet(cur, lockedValue)) nodes[index].compareAndSet(
                        lockedValue,
                        Node(removed, q.peek())
                    )
                }

                poll -> {
                    if (nodes[index].compareAndSet(cur, lockedValue)) nodes[index].compareAndSet(
                        lockedValue,
                        Node(removed, q.poll())
                    )
                }

                removed -> break

                else -> {
                    assert(false)
                }
            }
        }
    }

    fun poll(): E? {
        return process(Node(poll, null))
    }

    fun peek(): E? {
        return process(Node(peek, null))
    }

    fun add(element: E) {
        process(Node(add, element))
    }

    private fun process(node: Node<E>): E? {
        while (true) {
            if (lock.tryLock()) {
                var value: E? = null
                if (node.type == poll) value = q.poll()
                if (node.type == peek) value = q.peek()
                if (node.type == add) q.add(node.element)
                listProcess()
                lock.unlock()
                return value
            }
            val index = Random().nextInt(n)
            if (nodes[index].compareAndSet(null, node)) {
                while (true) {
                    val currentNode = nodes[index].value!!
                    if (currentNode.type == node.type) {
                        if (!lock.isLocked && nodes[index].compareAndSet(currentNode, null)) break
                        continue
                    }
                    if (currentNode.type == removed) {
                        nodes[index].compareAndSet(currentNode, null)
                        return currentNode.element
                    }
                }
            }
        }
    }
}
