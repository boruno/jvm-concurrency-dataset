import java.util.PriorityQueue
import kotlinx.atomicfu.*
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val array = atomicArrayOfNulls<Operation>(10)
    private val lock = atomic(false)
    private var random = Random(222)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var result: E? = null;
        while (true) {
            if (tryLock()) {
                result = q.poll()
                combine()
                unlock()
                return result
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, Operation('O', null))) {
                while (true) {
                    if (array[counter].value?.value != null) {
                        result = array[counter].value?.value as E?
                        array[counter].value = null
                        return result
                    }
                    if (tryLock()) {
                        if (array[counter].value?.value != null) {
                            result = array[counter].value?.value as E?
                            array[counter].value = null
                            combine()
                            unlock()
                            return result
                        }
                        array[counter].value = null
                        result = q.poll()
                        combine()
                        unlock()
                        return result
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
        var result: E? = null;
        while (true) {
            if (tryLock()) {
                result = q.peek()
                combine()
                unlock()
                return result
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, Operation('E', null))) {
                while (true) {
                    if (array[counter].value?.value != null) {
                        result = array[counter].value?.value as E?
                        array[counter].value = null
                        return result
                    }
                    if (tryLock()) {
                        if (array[counter].value?.value != null) {
                            result = array[counter].value?.value as E?
                            array[counter].value = null
                            combine()
                            unlock()
                            return result
                        }
                        array[counter].value = null
                        result = q.peek()
                        combine()
                        unlock()
                        return result
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        while (true) {
            if (tryLock()) {
                q.add(element)
                combine()
                unlock()
                return
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, Operation('A', element))) {
                while (true) {
                    if (array[counter].value == null) {
                        return
                    }
                    if (tryLock()) {
                        if (array[counter].value == null) {
                            combine()
                            unlock()
                            return
                        }
                        array[counter].value = null
                        q.add(element)
                        combine()
                        unlock()
                        return
                    }
                }
            }
        }
    }

    private fun combine() {
        for (i in 0..9) {
            val task = array[i]
            if (task.value != null) {
                when (task.value!!.operation) {
                    'O' -> {
                        if (task.value!!.value == null) {
//                            task.compareAndSet(Operation('O', null), Operation('O', q.poll()))
                            task.value!!.value = q.poll()
                        }
                    }

                    'E' -> {
                        if (task.value!!.value == null) {
                            task.value!!.value = q.peek()
                        }
                    }

                    'A' -> {
                        val element = task.value!!.value as E?
                        task.value = null
                        q.add(element)
                    }
                }

            }
        }
    }

    private fun tryLock(): Boolean {
        return lock.compareAndSet(false, update = true)
    }

    private fun unlock() {
        lock.value = false
    }

    data class Operation(var operation: Char?, var value: Any?)
}

