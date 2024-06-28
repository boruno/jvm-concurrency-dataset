import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val head: AtomicRef<Node<E>>
    private val tail: AtomicRef<Node<E>>
    private val lock = atomic<Boolean>(false)

    init {
        val firstNode = Node<E>(Operation.DONE, null)
        head = atomic(firstNode)
        tail = atomic(firstNode)
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val node = Node<E>(Operation.POLL, null)
        addToTail(node)

        while (true) {
            if (lock.compareAndSet(expect = false, update = true)) {
                val res: E?
                if (node.type == Operation.DONE) {
                    res = node.value
                } else {
                    node.type = Operation.DONE
                    res = q.poll()
                }
                help()
                lock.compareAndSet(expect = true, update = false)
                return res
            } else {
                for (i in 1..100) {
                    if (node.type == Operation.DONE) {
                        return node.value
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
        val node = Node<E>(Operation.PEEK, null)
        addToTail(node)

        while (true) {
            if (lock.compareAndSet(expect = false, update = true)) {
                val res: E?
                if (node.type == Operation.DONE) {
                    res = node.value
                } else {
                    node.type = Operation.DONE
                    res = q.peek()
                }
                help()
                lock.compareAndSet(expect = true, update = false)
                return res
            } else {
                for (i in 1..100) {
                    if (node.type == Operation.DONE) {
                        return node.value
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val node = Node(Operation.ADD, element)
        addToTail(node)

        while (true) {
            if (lock.compareAndSet(expect = false, update = true)) {
                if (node.type != Operation.DONE) {
                    node.type = Operation.DONE
                    q.add(element)
                }
                help()
                lock.compareAndSet(expect = true, update = false)
                return
            } else {
                for (i in 1..100) {
                    if (node.type == Operation.DONE) {
                        return
                    }
                }
            }
        }
    }

    private fun addToTail(node: Node<E>) {
        while (true) {
            val curTail = tail.value
            if (curTail.next.compareAndSet(null, node)) {
                tail.compareAndSet(curTail, node)
                break
            } else {
                tail.compareAndSet(curTail, curTail.next.value!!)
            }
        }
    }

    private fun help() {
        while (true) {
            val cur = head.value
            when (cur.type) {
                Operation.ADD -> {
                    q.add(cur.value)
                    cur.type = Operation.DONE
                }
                Operation.PEEK -> {
                    cur.value = q.peek()
                    cur.type = Operation.DONE
                }
                Operation.POLL -> {
                    cur.value = q.poll()
                    cur.type = Operation.DONE
                }
                Operation.DONE -> {

                }
            }
            if (cur.next.value != null) {
                head.value = cur.next.value!!
            } else {
                return
            }
        }
    }
}

enum class Operation {
    DONE,
    ADD,
    POLL,
    PEEK
}

class Node<E>(var type: Operation, var value: E?) {
    val next = atomic<Node<E>?>(null)
}