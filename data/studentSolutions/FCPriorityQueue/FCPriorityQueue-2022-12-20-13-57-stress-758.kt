
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

const val ARRAY_SIZE = 4

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arr = atomicArrayOfNulls<Node<E>?>(ARRAY_SIZE)
    private val lock = atomic<Boolean>(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            if (lock.compareAndSet(expect = false, update = true)) {
                val res = q.poll()
                help()
                lock.compareAndSet(expect = true, update = false)
                return res
            } else {
                val idx = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
                val node = Node<E>(null)
                node.type.value = NodeType.POLL
                if (arr[idx].compareAndSet(null, node)) {
                    for (i in 1..100) {
                        if (arr[idx].value!!.type.value!! >= NodeType.RESULT_PENDING) {
                            while(arr[idx].value!!.type.value != NodeType.RESULT) {}
                            if (arr[idx].compareAndSet(node, null)) {
                                return node.value
                            }
                        }
                    }
                    if (arr[idx].compareAndSet(node, null)) {
                        if (node.type.value!! >= NodeType.RESULT_PENDING) {
                            while(node.type.value != NodeType.RESULT) {}
                            return node.value
                        }
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
            if (lock.compareAndSet(expect = false, update = true)) {
                val res = q.peek()
                help()
                lock.compareAndSet(expect = true, update = false)
                return res
            } else {
                val idx = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
                val node = Node<E>(null)
                node.type.value = NodeType.PEEK
                if (arr[idx].compareAndSet(null, node)) {
                    for (i in 1..100) {
                        if (arr[idx].value!!.type.value!! >= NodeType.RESULT_PENDING) {
                            while(arr[idx].value!!.type.value != NodeType.RESULT) {}
                            if (arr[idx].compareAndSet(node, null)) {
                                return node.value
                            }
                        }
                    }
                    if (arr[idx].compareAndSet(node, null)) {
                        if (node.type.value!! >= NodeType.RESULT_PENDING) {
                            while(node.type.value != NodeType.RESULT) {}
                            return node.value
                        }
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
            if (lock.compareAndSet(expect = false, update = true)) {
                q.add(element)
                help()
                lock.compareAndSet(expect = true, update = false)
                return
            } else {
                val idx = ThreadLocalRandom.current().nextInt(0, ARRAY_SIZE)
                val node = Node<E>(element)
                node.type.value = NodeType.ADD
                if (arr[idx].compareAndSet(null, node)) {
                    for (i in 1..100) {
                        if (arr[idx].value!!.type.value!! >= NodeType.RESULT_PENDING) {
                            while(arr[idx].value!!.type.value != NodeType.RESULT) {}
                            if (arr[idx].compareAndSet(node, null)) {
                                return
                            }
                        }
                    }
                    if (arr[idx].compareAndSet(node, null)) {
                        if (node.type.value!! >= NodeType.RESULT_PENDING) {
                            while(node.type.value != NodeType.RESULT) {}
                            if (arr[idx].compareAndSet(node, null)) {
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun help() {
        for (i in 0 until ARRAY_SIZE) {
            val cur = arr[i].value?: continue
            when(cur.type.value) {
                NodeType.ADD -> {
                    cur.type.compareAndSet(NodeType.ADD, NodeType.RESULT_PENDING)
                    q.add(cur.value)
                    cur.type.compareAndSet(NodeType.RESULT_PENDING, NodeType.RESULT)
                }
                NodeType.POLL -> {
                    cur.type.compareAndSet(NodeType.POLL, NodeType.RESULT_PENDING)
                    cur.value = q.poll()
                    cur.type.compareAndSet(NodeType.RESULT_PENDING, NodeType.RESULT)
                }
                NodeType.PEEK -> {
                    cur.type.compareAndSet(NodeType.PEEK, NodeType.RESULT_PENDING)
                    cur.value = q.peek()
                    cur.type.compareAndSet(NodeType.RESULT_PENDING, NodeType.RESULT)
                }
                else -> {}
            }
        }
    }
}

enum class NodeType {
    ADD,
    POLL,
    PEEK,
    RESULT_PENDING,
    RESULT
}

class Node<E>(var value: E?) {
    val type = atomic<NodeType?>(null)
//    val next = atomic<Node<E>?>(null)
}