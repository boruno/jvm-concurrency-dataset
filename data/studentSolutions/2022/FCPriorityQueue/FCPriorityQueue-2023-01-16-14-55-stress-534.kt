import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val locked = ReentrantLock()
    private val size = Runtime.getRuntime().availableProcessors() * 4;
    private val fcArray = atomicArrayOfNulls<Node<E>>(size)


    private class Node<E>(val op: Op, var value: E?)


    enum class Op {
        POLL, ADD, DONE
    }


    private fun help() {
        for (i in 0 until size) {
            val node = fcArray[i].value!!
            if (node.op == Op.ADD) {
                synchronized(q){
                    q.add(node.value!!)
                    fcArray[i].compareAndSet(expect = node, update = Node(Op.DONE, null))
                }
            } else if (node.op == Op.POLL) {
                val res = q.poll()
                fcArray[i].compareAndSet(expect = node, update = Node(Op.DONE, res))
            }
        }
    }

    private fun makeOperation(op: Op, value: E?): E? {
        if (locked.tryLock()) {
            val result = if (op == Op.ADD) {
                q.add(value!!)
                null
            } else {
                q.poll()
            }
            help()
            locked.unlock()
            return result
        } else {
            val index = Random().nextInt() % size
            while (!fcArray[index].compareAndSet(expect = null, update = Node(op, value))) {}
            while (true) {
                val node = fcArray[index].getAndSet(null)!!
                if (node.op == Op.DONE) {
                    return node.value
                }
                if (locked.tryLock()) {
                    val x = fcArray[index].getAndSet(null)!!
                    var result: E? = null
                    when (x.op) {
                        Op.ADD -> q.add(x.value!!)
                        Op.POLL -> result = q.poll()
                        Op.DONE -> result = x.value!!
                    }
                    help()
                    locked.unlock()
                    return result
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
        return makeOperation(Op.POLL, null)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        makeOperation(Op.ADD, element)
    }
}