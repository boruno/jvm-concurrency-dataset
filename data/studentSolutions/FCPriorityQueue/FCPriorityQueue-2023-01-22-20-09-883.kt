import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*



private const val SIZE = 32
class FCPriorityQueue<E : Comparable<E>> {
    private val pq = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Node<E>>(SIZE)
    private val lock = atomic(false)

    private class Node<E>(var element: E?, val type: OP)

    private enum class OP {
        ADD, POLL, PEEK, PROGRESS, END
    }

    private fun trav() {
        if (lock.compareAndSet(expect = false, update = true)) {
            for (i in 0 until SIZE) {
                val op = fcArray[i]
                val param = op.value ?: continue
                if (param.type == OP.ADD) {
                    if (fcArray[i].compareAndSet(param, Node(param.element, OP.PROGRESS))) {
                        pq.add(param.element)
                        fcArray[i].getAndSet(Node(param.element, OP.END))
                    }
                }
                if (param.type == OP.POLL) {
                    if (fcArray[i].compareAndSet(param, Node(param.element, OP.PROGRESS))) {
                        param.element = pq.poll()
                        fcArray[i].getAndSet(Node(param.element, OP.END))
                    }
                }
                if (param.type == OP.PEEK) {
                    if (fcArray[i].compareAndSet(param, Node(param.element, OP.PROGRESS))) {
                        param.element = pq.peek()
                        fcArray[i].getAndSet(Node(param.element, OP.END))
                    }
                }
            }
            lock.compareAndSet(expect = true, update = false)
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var flag = -1
        while (true) {
            if (flag == -1) {
                for (position in 0 until SIZE) {
                    if (fcArray[position].compareAndSet(null, Node(null, OP.POLL))) {
                        flag = position
                        break
                    }
                }
            } else {
                if (!lock.value) {
                    trav()
                }
                val result = fcArray[flag].value!!
                if (result.type == OP.END) {
                    fcArray[flag].compareAndSet(result, null)
                    return result.element
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var flag = -1
        while (true) {
            if (flag == -1) {
                for (position in 0 until SIZE) {
                    if (fcArray[position].compareAndSet(null, Node(null, OP.PEEK))) {
                        flag = position
                        break
                    }
                }
            } else {
                if (!lock.value) {
                    trav()
                }
                val res = fcArray[flag].value!!
                if (res.type == OP.END) {
                    fcArray[flag].compareAndSet(res, null)
                    return res.element
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var flag = -1
        while (true) {
            if (flag == -1) {
                for (position in 0 until SIZE) {
                    if (fcArray[position].compareAndSet(null, Node(element, OP.ADD))) {
                        flag = position
                        break
                    }
                }
            } else {
                if (!lock.value) {
                    trav()
                }
                val res = fcArray[flag].value!!
                if (res.type == OP.END) {
                    fcArray[flag].compareAndSet(res, null)
                }
            }
        }
    }
}


