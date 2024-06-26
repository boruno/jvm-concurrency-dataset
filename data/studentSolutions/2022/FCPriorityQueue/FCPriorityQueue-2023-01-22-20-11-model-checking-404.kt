import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val pq = PriorityQueue<E>()
    private val size = 32
    private val fcArray = atomicArrayOfNulls<Node<E>>(size)
    private val lock = atomic(false)


    private class Node<E>(var element: E?, val type: OP)

    private enum class OP {
        ADD, POLL, PROGRESS, END
    }

    private fun fortify(operation: Node<E>): Node<E> {
        if (operation.type == OP.POLL) {
            operation.element = pq.poll()
        }
        if (operation.type == OP.ADD) {
            pq.add(operation.element)
        }

        return Node(operation.element, OP.END)
    }

    private fun combine() {
        if (!lock.compareAndSet(expect = false, update = true)) return
        for (i in 0 until size) {
            val op = fcArray[i]
            val real = op.value ?: continue
            if (real.type == OP.ADD) {
                if (fcArray[i].compareAndSet(real, Node(real.element, OP.PROGRESS))) {
                    real.element = pq.poll();
                    fcArray[i].getAndSet(Node(real.element, OP.END))
                }
            }

            if (real.type != OP.END && real.type != OP.PROGRESS) {
                if (fcArray[i].compareAndSet(real, Node(real.element, OP.PROGRESS))) {
                    val result = fortify(real)
                    fcArray[i].getAndSet(result)
                }
            }
        }
        lock.compareAndSet(expect = true, update = false)
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
                for (position in 0 until size) {
                    if (fcArray[position].compareAndSet(null, Node(null, OP.POLL))) {
                        flag = position
                        break
                    }
                }
            } else {
                if (!lock.value) {
                    combine()
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
        return pq.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var flag = -1
        while (true) {
            if (flag == -1) {
                for (position in 0 until size) {
                    if (fcArray[position].compareAndSet(null, Node(element, OP.ADD))) {
                        flag = position
                        break
                    }
                }
            } else {
                if (!lock.value) {
                    combine()
                }
                val res = fcArray[flag].value!!
                if (res.type == OP.END) {
                    fcArray[flag].compareAndSet(res, null)
                }
            }
        }
    }
}


