import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val locked = atomic(false)
    private val fcArray = atomicArrayOfNulls<Op<E>?>(FC_ARRAY_SIZE)

    private fun tryLock() = locked.compareAndSet(expect = false, update = true)

    private fun unlock() {
        locked.value = false
    }

    private fun findIndex() = Random().nextInt(FC_ARRAY_SIZE)

    private fun applyOps() {
        (0 until FC_ARRAY_SIZE).forEach { ind ->
            val curVal = fcArray[ind].value
            if (curVal != null) {
                when (curVal) {
                    is Op.Add -> {
                        q.add(curVal.element)
                        curVal.isReady = true
                    }

                    is Op.Peek -> {
                        curVal.result = q.peek()
                    }

                    is Op.Poll -> {
                        curVal.result = q.poll()
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
        val op = Op.Poll<E>()
        while (true) {
            if (tryLock()) { // it is a combiner now
                applyOps()
                val result = q.poll()
                unlock()
                return result
            }

            val ind = findIndex()
            if (fcArray[ind].compareAndSet(null, op)) {
                while (true) {
                    if (tryLock()) {
                        applyOps()
                        unlock()
                    }
                    (fcArray[ind].value as Op.Poll).result?.let {
                        fcArray[ind].value = null
                        return it
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
        val op = Op.Peek<E>()
        while (true) {
            if (tryLock()) { // it is a combiner now
                applyOps()
                val result = q.peek()
                unlock()
                return result
            }

            val ind = findIndex()
            if (fcArray[ind].compareAndSet(null, op)) {
                while (true) {
                    if (tryLock()) {
                        applyOps()
                        unlock()
                    }
                    (fcArray[ind].value as Op.Peek).result?.let {
                        fcArray[ind].value = null
                        return it
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val op = Op.Add<E>(element)
        while (true) {
            if (tryLock()) { // it is a combiner now
                applyOps()
                q.add(element)
                unlock()
            }

            val ind = findIndex()
            if (fcArray[ind].compareAndSet(null, op)) {
                while (true) {
                    if (tryLock()) {
                        applyOps()
                        unlock()
                    }
                    if ((fcArray[ind].value as Op.Add).isReady) {
                        fcArray[ind].value = null
                    }
                }
            }
        }
    }
}

private const val FC_ARRAY_SIZE = 50

sealed class Op<E> {
    data class Add<E>(val element: E) : Op<E>() {
        var isReady = false
    }

    class Peek<E> : Op<E>() {
        var result: E? = null
    }

    class Poll<E> : Op<E>() {
        var result: E? = null
    }
}
