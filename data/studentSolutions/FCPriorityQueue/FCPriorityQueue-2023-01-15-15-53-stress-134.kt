import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import java.lang.Exception
import java.util.*
import java.util.concurrent.*

const val ARRAY_SIZE = 10

sealed class Op
object PeekOp : Op()
object PollOp : Op()
data class PutOp<E>(val element: E) : Op()
data class ReturnOp<E>(val result: E?): Op()

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arr = atomicArrayOfNulls<Op>(ARRAY_SIZE)
    private val random = ThreadLocalRandom.current()
    private val lock: ReentrantLock = ReentrantLock()

    fun putOp(op: Op): E? {
        val ix = random.nextInt(ARRAY_SIZE)
        while (true) {
            if(lock.tryLock()) {
                // если нас уже посчитали
                val v = arr[ix].value
                if(v is ReturnOp<*>) {
                    lock.unlock()
                    arr[ix].compareAndSet(v, null)
                    return v.result as E?
                }
                // удаляем свою операцию чтоб не считать дважды
                arr[ix].compareAndSet(op, null)
                val res = runOp(op)
                runThrowQueue()
                lock.unlock()
                return res
            }
            if(arr[ix].compareAndSet(null, op)) {
                continue
            }
            when(val res = arr[ix].value) {
                is ReturnOp<*> -> {
                    arr[ix].compareAndSet(res, null)
                    return res.result as E?
                }
                else -> continue
            }
        }
        return null
    }

    private fun runOp(op: Op): E? = when(op) {
        PeekOp -> q.peek()
        PollOp -> q.poll()
        is PutOp<*> -> { q.add(op.element as E); null }
        is ReturnOp<*> -> { throw  Exception("Invalid move") }
    }
    private fun runThrowQueue() {
        for(i in 0 until 10) {
            when(val op = arr[i].value) {
                PeekOp -> arr[i].compareAndSet(op, ReturnOp(q.peek()))
                PollOp -> arr[i].compareAndSet(op, ReturnOp(q.poll()))
                is PutOp<*> -> arr[i].compareAndSet(op, ReturnOp(q.add(op.element as E)))
                is ReturnOp<*> -> continue
                null -> continue
            }
        }
    }

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val op = PollOp
        return putOp(op)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val op = PeekOp
        return putOp(op)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val op = PutOp(element)
        putOp(op)
        return
    }
}