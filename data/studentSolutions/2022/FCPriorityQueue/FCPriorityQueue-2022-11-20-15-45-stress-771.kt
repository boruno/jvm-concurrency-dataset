import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val locked = atomic(false)
    private val fcArray = atomicArrayOfNulls<Any?>(FC_ARRAY_SIZE)
    private val q = PriorityQueue<E>()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        while (true) {
            if (locked.compareAndSet(expect = false, update = true)) {
                return pollWithLock()
            } else {
                val i = ThreadLocalRandom.current().nextInt() % FC_ARRAY_SIZE
                val operation = Operation(OperationType.POLL, null)
                if (!fcArray[i].compareAndSet(null, operation)) {
                    continue
                }
                for (tmp in 0 until ATTEMPTS) {
                    if (locked.compareAndSet(expect = false, update = true)) {
                        fcArray[i].value = null
                        return pollWithLock()
                    } else if (fcArray[i].value !is Operation<*>) {
                        break
                    }
                }
                if (!fcArray[i].compareAndSet(operation, null)) {
                    do {
                        val cell = fcArray[i].value
                    } while (cell is Work)
                    @Suppress("UNCHECKED_CAST")
                    val element = (fcArray[i].value as Res<*>).value as E?
                    fcArray[i].value = null
                    return element
                }
            }
        }
    }

    private fun pollWithLock(): E? {
        try {
            val element = q.poll()
            combine()
            return element
        } finally {
            locked.value = false
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        while (true) {
            if (locked.compareAndSet(expect = false, update = true)) {
                return peekWithLock()
            } else {
                val i = ThreadLocalRandom.current().nextInt() % FC_ARRAY_SIZE
                val operation = Operation(OperationType.PEEK, null)
                if (!fcArray[i].compareAndSet(null, operation)) {
                    continue
                }
                for (tmp in 0 until ATTEMPTS) {
                    if (locked.compareAndSet(expect = false, update = true)) {
                        fcArray[i].value = null
                        return peekWithLock()
                    } else if (fcArray[i].value !is Operation<*>) {
                        break
                    }
                }
                if (!fcArray[i].compareAndSet(operation, null)) {
                    do {
                        val cell = fcArray[i].value
                    } while (cell is Work)
                    @Suppress("UNCHECKED_CAST")
                    val element = (fcArray[i].value as Res<*>).value as E?
                    fcArray[i].value = null
                    return element
                }
            }
        }
    }

    private fun peekWithLock(): E? {
        try {
            val element = q.peek()
            combine()
            return element
        } finally {
            locked.value = false
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            if (locked.compareAndSet(expect = false, update = true)) {
                addWithLock(element)
            } else {
                val i = ThreadLocalRandom.current().nextInt() % FC_ARRAY_SIZE
                val operation = Operation(OperationType.ADD, element)
                if (!fcArray[i].compareAndSet(null, operation)) {
                    continue
                }
                for (tmp in 0 until ATTEMPTS) {
                    if (locked.compareAndSet(expect = false, update = true)) {
                        fcArray[i].value = null
                        addWithLock(element)
                    } else if (fcArray[i].value !is Operation<*>) {
                        break
                    }
                }
                if (!fcArray[i].compareAndSet(operation, null)) {
                    do {
                        val cell = fcArray[i].value
                    } while (cell is Work)
                    return
                }
            }
        }
    }

    private fun addWithLock(element: E) {
        try {
            q.add(element)
            combine()
        } finally {
            locked.value = false
        }
    }

    private fun combine() {
        for (i in 0 until FC_ARRAY_SIZE) {
            val operation = fcArray[i].value ?: continue
            if (operation is Operation<*>) {
                if (!fcArray[i].compareAndSet(operation, Work())) {
                    continue
                }
                when (operation.type) {
                    OperationType.POLL -> {
                        val element = q.poll()
                        fcArray[i].value = Res(element)
                    }
                    OperationType.PEEK -> {
                        val element = q.peek()
                        fcArray[i].value = Res(element)
                    }
                    OperationType.ADD -> {
                        @Suppress("UNCHECKED_CAST")
                        q.add(operation.arg as E)
                        fcArray[i].value = Res(null)
                    }
                }
            } else {
                continue
            }
        }
    }
}

private enum class OperationType {
    POLL, PEEK, ADD,
}

private class Operation<E>(val type: OperationType, val arg: E?)

private class Work
private class Res<E>(val value: E)

const val FC_ARRAY_SIZE = 10
const val ATTEMPTS = 100
