import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import Operation.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val lock = atomic(false)
    private val array = atomicArrayOfNulls<Node<E>>(THREADS)
    private val rnd: Random = Random()

    private fun tryLock() = lock.compareAndSet(expect = false, update = true)

    private fun unlock() {
        lock.value = false
    }

    private fun combine() {
        for (ind in 0 until THREADS) {
            val curNode = array[ind].value
            if (curNode != null) {
                when (curNode.op) {
                    Operation.POLL -> {
                        val ans = q.peek()
                        if (array[ind].compareAndSet(curNode, Node(Operation.DONE, ans))) {
                            q.poll()
                        }
                    }
                    Operation.PEEK -> {
                        val ans = q.peek()
                        array[ind].compareAndSet(curNode, Node(Operation.DONE, ans))
                    }
                    Operation.ADD -> {
                        if (array[ind].compareAndSet(curNode, Node(Operation.DONE, null))) {
                            q.add(curNode.x)
                        }
                    }
                    Operation.DONE -> {
                        continue
                    }
                }
            }
        }
    }

    private fun commonMethod(node: Node<E>): Node<E>? {
        while (true) {
            val c = rnd.nextInt(THREADS)
            val a = Math.max(c - 2, 0)
            val b = Math.min(c + 3, THREADS)
            for (i in a until b) {
                if (array[i].compareAndSet(null, node)) {
                    while (true) {
                        val newNode = array[i].value
                        if (newNode != null && newNode.op == Operation.DONE) {
                            array[i].compareAndSet(newNode, null)
                            return newNode
                        } else {
                            if (array[i].compareAndSet(newNode, null)) {
                                return null
                            }
                        }
                    }
                }
            }
        }
    }

    private fun common(key: Operation, f: () -> E?): E? {
        while (true) {
            if (tryLock()) {
                try {
                    combine()
                    f()
                } finally {
                    unlock()
                }
            } else {
                val node = Node<E>(key, null)
                return when(key) {
                    in POLL..PEEK -> {
                        val newNode = commonMethod(node) ?: continue
                        newNode.x
                    }
                    ADD -> {
                        commonMethod(node) ?: continue
                        null
                    }
                    else -> throw Exception()
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
        return common(POLL) { q.poll() }
        /*while (true) {
            if (tryLock()) {
                try {
                    combine()
                    return q.poll()
                } finally {
                    unlock()
                }
            } else {
                val node = Node<E>(Operation.POLL, null)
                val newNode = commonMethod(node) ?: continue
                return newNode.x
            }
        }*/
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return common(PEEK) { q.peek() }
        /*while (true) {
            if (tryLock()) {
                try {
                    combine()
                    return q.peek()
                } finally {
                    unlock()
                }
            } else {
                val node = Node<E>(Operation.PEEK, null)
                val newNode = commonMethod(node) ?: continue
                return newNode.x
            }
        }*/
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        common(ADD) {
            q.add(element)
            null
        }
        /*while (true) {
            if (tryLock()) {
                try {
                    combine()
                    q.add(element)
                    return
                } finally {
                    unlock()
                }
            } else {
                val node = Node(Operation.ADD, element)
                commonMethod(node) ?: continue
                return
            }
        }*/
    }
}

enum class Operation {
    POLL, PEEK, ADD, DONE
}

class Node<E>(val op: Operation, val x: E?)
private val THREADS: Int = Runtime.getRuntime().availableProcessors()
