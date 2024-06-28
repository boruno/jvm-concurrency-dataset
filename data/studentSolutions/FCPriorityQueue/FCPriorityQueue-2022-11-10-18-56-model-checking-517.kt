import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

abstract class Operation<E> {

    protected var result = false

    fun done(): Boolean {
        return result
    }

    abstract fun perform(q : PriorityQueue<E>)

    abstract fun result() : E?
}

class InsertOperation<E>(val e: E) : Operation<E>() {

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

    private fun tryLock() : Boolean {
        if (locked.compareAndSet(false, true)) {
            l_c++
            return true
        }
        return false
    }

    private var l_c = 0
    private var ul_c = 0

    private fun unlock() {
        assert(locked.value)
        locked.value = false
        ul_c++
    }

    private fun emplaceOperation(o: Operation<E>) : Int {
        var i = -1
        do {
            i = Random().nextInt(FC_SIZE)
        } while (!fc[i].compareAndSet(null, o))
        return i
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

        val i = emplaceOperation(o)
        while (true) {
            if (o.done()) {
                return o.result()
            }
            if (tryLock()) {
                if (!o.done()) {
                    if (fc[i].compareAndSet(o, null)) {
                        return opAndHelp(o)
                    } else {
                        unlock()
                    }
                } else {
                    unlock()
                    return o.result()
                }
            }
        }

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
            val o  = fc[i].value
            if (o?.done() == false) {
                if (fc[i].compareAndSet(o, null)) {
                    fc[i].value?.perform(q)
                } else {
                    println(i)
                }
            }
        }
    }


}