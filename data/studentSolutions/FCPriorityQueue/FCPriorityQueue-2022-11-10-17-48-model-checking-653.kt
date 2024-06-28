import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

abstract class Operation<E> {

    protected var result = false
    abstract fun done(): Boolean

    abstract fun perform(q : PriorityQueue<E>)

    abstract fun result() : E?
}

class InsertOperation<E>(val e: E) : Operation<E>() {
    override fun done(): Boolean {
        return result
    }

    override fun result(): E? {
        return null
    }

    override fun perform(q: PriorityQueue<E>) {
        q.add(e)
        result = true
    }
}

class ExtractOperation<E>(val remove: Boolean = true) : Operation<E>() {
    var value: E? = null
    override fun done(): Boolean {
        return value != null
    }

    override fun result(): E? {
        return value
    }

    override fun perform(q: PriorityQueue<E>) {
        value = if (remove) q.poll() else q.peek()
        result = true
    }
}

class FCPriorityQueue<E : Comparable<E>> {

    private val FC_SIZE = 6

    private val q = PriorityQueue<E>()
    private val fc = atomicArrayOfNulls<Operation<E>>(FC_SIZE)

    private val locked = atomic(false)

    private fun tryLock() = locked.compareAndSet(false, true)

    private fun unlock() {
        locked.value = false
    }

    private fun emplaceOperation(o: Operation<E>) {
        do {
            val i = Random().nextInt(FC_SIZE)
        } while (!fc[i].compareAndSet(null, o))
    }


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return op(ExtractOperation(true))
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return op(ExtractOperation(false))
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        op(InsertOperation(element))
    }

    private fun op(o: Operation<E>) : E? {
        if (tryLock()) {
            return opAndHelp(o)
        }

        emplaceOperation(o)
        while (!o.done() && !tryLock()) {
        }


        if (!o.done()) {
            return opAndHelp(o)
        }

        return o.result()
    }

    private fun opAndHelp(op : Operation<E>) : E? {
        assert(locked.value)
        op.perform(q)
        help()
        unlock()
        return op.result()
    }

    private fun help() {
        assert(locked.value)
        for (i in 0 until FC_SIZE) {
            if (fc[i].value?.done() == false) {
                fc[i].value?.perform(q)
                fc[i].value = null
            }
        }
    }


}