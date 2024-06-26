import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.max
import kotlin.math.min
enum class Operation {
    POLL, PEEK, ADD, DONE
}

class Node<E>(val op: Operation, val x: E?)

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arrSize = 20
    private val lock: AtomicBoolean = atomic(false)
    private val array = atomicArrayOfNulls<Node<E>>(arrSize)
    private val rnd: Random = Random()

    private fun tryLock(): Boolean {
        return lock.compareAndSet(false, true)
    }

    private fun unlock() {
        lock.getAndSet(false)
    }

    private fun combine() {
        for (ind in 0..(arrSize - 1)) {
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
            val c = rnd.nextInt(arrSize)
            val a = max(c - 2, 0)
            val b = min(c + 2, arrSize - 1)
            for (i in a..b) {
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

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
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
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
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
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
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
        }
    }
}

/*class FCPriorityQueue<E : Comparable<E>> {
    companion object {
        private class Task<E>(val operation: () -> E?, var finished: Boolean = false) {
            var result: E? = null
            val notFinished: Boolean
                get() {
                    return  !finished;
                }
            fun run() {
                result = operation()
                finished = true
            }
        }
    }
    private val q = PriorityQueue<E>()
    private val tasks = atomicArrayOfNulls<Task<E>>(THREADS)
    private val lock = atomic(false)

    private fun make(operation: () -> E?): E? {
        val task = Task(operation)
        var i: Int
        do i = ThreadLocalRandom.current().nextInt(THREADS)
        while (!tasks[i].compareAndSet(null, task))
        do if (lock.compareAndSet(false, true)) {
            (0 until THREADS)
                .asSequence()
                .mapNotNull { tasks[it].value }
                .filterNot { it.finished }
                .forEach {it.run()}
        } while (!task.finished)
        tasks[i].getAndSet(null)
        return task.result
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return make{q.poll()}
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return make{q.peek()}
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        make {
            q.add(element)
            null
        }
    }
}*/
private val THREADS = Runtime.getRuntime().availableProcessors()