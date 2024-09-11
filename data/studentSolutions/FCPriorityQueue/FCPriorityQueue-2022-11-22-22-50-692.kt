import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.locks.ReentrantLock

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Task<E>>(FC_ARRAY_SIZE)
    private val lock = ReentrantLock()


    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return q.poll()
//        if (lock.tryLock()) {
//            val result = q.poll()
//            help()
//            return result
//        } else {
//            val random = Random()
//            var index: Int
//            while (true) {
//                index = random.nextInt(FC_ARRAY_SIZE)
//                if (fcArray[index].compareAndSet(null, Task(Operation.POLL, null))) {
//                    break
//                }
//            }
//            while (true) {
//                if (lock.tryLock()) {
//                    val result: E? = if (fcArray[index].value?.operation == Operation.DONE) {
//                        fcArray[index].value?.element
//                    } else {
//                        q.poll()
//                    }
//                    fcArray[index].value = null
//                    help()
//                    return result
//                }
//                if (fcArray[index].value?.operation == Operation.DONE) {
//                    val result = fcArray[index].value?.element
//                    fcArray[index].value = null
//                    return result
//                }
//            }
//        }
    }

    private fun help() {
        for (i in 0 until FC_ARRAY_SIZE) {
            if (fcArray[i].value == null) {
                continue
            }
            when (fcArray[i].value?.operation) {
                Operation.POLL -> fcArray[i].value = Task(Operation.DONE, q.poll())
                Operation.ADD  -> {
                    q.add(fcArray[i].value?.element)
                    fcArray[i].value = Task(Operation.DONE, null)
                }
                Operation.PEEK -> fcArray[i].value = Task(Operation.DONE, q.peek())
                else -> { /* unreachable */ }
            }
        }
        lock.unlock()
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return q.peek()
//        if (lock.tryLock()) {
//            val result = q.peek()
//            help()
//            return result
//        } else {
//            val random = Random()
//            var index: Int
//            while (true) {
//                index = random.nextInt(FC_ARRAY_SIZE)
//                if (fcArray[index].compareAndSet(null, Task(Operation.PEEK, null))) {
//                    break
//                }
//            }
//            while (true) {
//                if (lock.tryLock()) {
//                    val result: E? = if (fcArray[index].value?.operation == Operation.DONE) {
//                        fcArray[index].value?.element
//                    } else {
//                        q.peek()
//                    }
//                    fcArray[index].value = null
//                    help()
//                    return result
//                }
//                if (fcArray[index].value?.operation == Operation.DONE) {
//                    val result = fcArray[index].value?.element
//                    fcArray[index].value = null
//                    return result
//                }
//            }
//        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        q.add(element)
    }
//        if (lock.tryLock()) {
//            q.add(element)
//            help()
//        } else {
//            val random = Random()
//            var index: Int
//            while (true) {
//                index = random.nextInt(FC_ARRAY_SIZE)
//                if (fcArray[index].compareAndSet(null, Task(Operation.ADD, element))) {
//                    break
//                }
//            }
//            while (true) {
//                if (lock.tryLock()) {
//                    if (fcArray[index].value?.operation != Operation.DONE) {
//                        q.add(element)
//                    }
//                    fcArray[index].value = null
//                    help()
//                    return
//                }
//                if (fcArray[index].value?.operation == Operation.DONE) {
//                    fcArray[index].value = null
//                    return
//                }
//            }
//        }
//    }
}

private enum class Operation {
    POLL, PEEK, ADD, DONE
}
private data class Task<E>(val operation: Operation, val element: E?)

private const val FC_ARRAY_SIZE = 8