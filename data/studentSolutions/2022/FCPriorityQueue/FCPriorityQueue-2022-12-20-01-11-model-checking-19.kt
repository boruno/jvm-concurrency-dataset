import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

const val ARRAY_SIZE = 8

enum class Operation {
    POLL,
    PEEK,
    ADD,
    DONE
}

data class ArrayItem<E>(val op: Operation, val value: E?)

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val arr = atomicArrayOfNulls<ArrayItem<E>?>(ARRAY_SIZE)
    private val locked = atomic(false)
    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val idx = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        while (true) {
            if (tryLock()) {
                break
            } else {
                if (!arr[idx].compareAndSet(null, ArrayItem(Operation.POLL, null))) {
                    if (arr[idx].value!!.op == Operation.DONE) {
                        return arr[idx].value!!.value
                    }
                }
            }
        }

        val res = q.poll()
        help()
        unlock()
        return res
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        val idx = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        while (true) {
            if (tryLock()) {
                break
            } else {
                if (!arr[idx].compareAndSet(null, ArrayItem(Operation.PEEK, null))) {
                    if (arr[idx].value!!.op == Operation.DONE) {
                        return arr[idx].value!!.value
                    }
                }
            }
        }

        val res = q.peek()
        help()
        unlock()
        return res
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        val idx = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        while (true) {
            if (tryLock()) {
                break
            } else {
                if (!arr[idx].compareAndSet(null, ArrayItem(Operation.ADD, element))) {
                    if (arr[idx].value!!.op == Operation.DONE) {
                        return
                    }
                }
            }
        }

        q.add(element)
        help()
        unlock()
    }

    private fun help() {
        for (i in 0 until ARRAY_SIZE) {
            val arrValue = arr[i].value
            if (arrValue != null) {
                when(arrValue.op) {
                    Operation.POLL -> {
                        arr[i].compareAndSet(arrValue, ArrayItem(Operation.DONE, q.poll()))
                    }
                    Operation.PEEK -> {
                        arr[i].compareAndSet(arrValue, ArrayItem(Operation.DONE, q.peek()))
                    }
                    Operation.ADD -> {
                        arr[i].compareAndSet(arrValue, ArrayItem(Operation.DONE, null))
                    }
                    Operation.DONE -> {}
                }
            }
        }
    }

    private fun tryLock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        locked.value = false
    }
}