import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val size = 64;
    private val q = PriorityQueue<E>()
    private val locked = atomic(false);


    private class Node<E>(val op: Op, var value: E?)

    private val fcArray = atomicArrayOfNulls<Node<E>>(size)

    enum class Op {
        POLL, PEEK, ADD, DONE
    }

    private fun lock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        locked.value = false
    }

    private fun isLocked(): Boolean {
        return locked.value
    }

    private fun makeOperation(op: Op, value: E?): E? {
        if (lock()) {
            val res = applyValue(op, value)
            help()
            unlock()
            return res
        } else {
            val sharedIndex = shareValue(op, value)
            while (true) {
                if (sharedIndex != -1) {
                    if (fcArray[sharedIndex].value!!.op == Op.DONE) {
                        val res = fcArray[sharedIndex].value
                        fcArray[sharedIndex].compareAndSet(expect = res, update = null)
                        return res!!.value
                    }
                } else if (lock()) {
                    val res = applyValue(op, value)
                    help()
                    unlock()
                    return res
                }
            }

        }
    }

    private fun shareValue(op: Op, value: E?): Int {
        val node = Node(op, value)
        val index = (Thread.currentThread().id % size).toInt()
        for (i in 0 until size) {
            val j = (index + i) % size
            if (fcArray[j].compareAndSet(expect = null, update = node)) {
                return j
            }
        }
        return -1
    }

    private fun help(): E? {
        for (i in 0 until size) {
            val task = fcArray[i].value ?: continue
            if (task.op != Op.DONE) {
                val res = applyValue(task.op, task.value)
                fcArray[i].compareAndSet(expect = task, update = Node(Op.DONE, res))
            }
        }
        return null
    }

    private fun applyValue(op: Op, element: E? = null): E? {
        return when (op) {
            Op.POLL -> q.poll()
            Op.PEEK -> q.peek()
            Op.ADD -> {
                q.add(element!!)
                null
            }

            Op.DONE -> null
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return makeOperation(Op.POLL, null)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return makeOperation(Op.PEEK, null)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        makeOperation(Op.ADD, element)
    }
}