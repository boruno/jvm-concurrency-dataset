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
        val firstNode = Node<E>(Operation.PEEK, null)
        firstNode.finished = true
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
                if (node.finished) {
                    res = node.value
                } else {
                    res = q.poll()
                    println("[${Thread.currentThread().id}] poll $res")
                    node.finished = true
                }
                help()
                lock.compareAndSet(expect = true, update = false)
                return res
            } else {
                for (i in 1..100) {
                    if (node.finished) {
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
                if (node.finished) {
                    res = node.value
                } else {
                    res = q.peek()
                    node.finished = true
                }
                help()
                lock.compareAndSet(expect = true, update = false)
                return res
            } else {
                for (i in 1..100) {
                    if (node.finished) {
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
                if (!node.finished) {
                    q.add(element)
                    println("[${Thread.currentThread().id}] add $element")
                    node.finished = true
                }
                help()
                lock.compareAndSet(expect = true, update = false)
                return
            } else {
                for (i in 1..100) {
                    if (node.finished) {
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
                    println("[${Thread.currentThread().id}] add ${cur.value}")
                    cur.finished = true
                }
                Operation.PEEK -> {
                    cur.value = q.peek()
                    cur.finished = true
                }
                Operation.POLL -> {
                    cur.value = q.poll()
                    cur.finished = true
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
    ADD,
    POLL,
    PEEK
}

class Node<E>(var type: Operation, var value: E?) {
    var finished = false
    val next = atomic<Node<E>?>(null)
}