import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    class Node<E>(val type: Type, val element: E?)
    enum class Type {
        ADD, PEEK, POLL, LOCKED, REMOVED
    }

    private val q = PriorityQueue<E>()
    private val lock = ReentrantLock()
    private val n = Random().nextInt(10, 20)
    private val nodes = atomicArrayOfNulls<Node<E>>(n)

    private fun listProcess() {
        for (index in 0 until n) {
            val cur = nodes[index].value ?: continue
            val lockedValue = Node<E>(Type.LOCKED, null)
            when (cur.type) {
                Type.ADD -> {
                    if (nodes[index].compareAndSet(cur, lockedValue)) {
                        q.add(cur.element)
                        nodes[index].compareAndSet(lockedValue, Node(Type.REMOVED, null))
                    }
                }

                Type.PEEK -> {
                    if (nodes[index].compareAndSet(cur, lockedValue)) nodes[index].compareAndSet(
                        lockedValue,
                        Node(Type.REMOVED, q.peek())
                    )
                }

                Type.POLL -> {
                    if (nodes[index].compareAndSet(cur, lockedValue)) nodes[index].compareAndSet(
                        lockedValue,
                        Node(Type.REMOVED, q.poll())
                    )
                }

                Type.REMOVED -> break

                else -> {
                    assert(false)
                }
            }
        }
    }

    fun poll(): E? {
        return process(Node(Type.POLL, null))
    }

    fun peek(): E? {
        return process(Node(Type.PEEK, null))
    }

    fun add(element: E) {
        process(Node(Type.ADD, element))
    }

    private fun process(node: Node<E>): E? {
        while (true) {
            if (lock.tryLock()) {
                var value: E? = null
                if (node.type == Type.POLL) value = q.poll()
                if (node.type == Type.PEEK) value = q.peek()
                if (node.type == Type.ADD) q.add(node.element)
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
                    if (currentNode.type == Type.REMOVED) {
                        nodes[index].compareAndSet(currentNode, null)
                        return currentNode.element
                    }
                }
            }
        }
    }
}