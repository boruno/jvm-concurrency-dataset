import java.util.PriorityQueue
import kotlinx.atomicfu.*
import java.util.concurrent.locks.ReentrantLock
import java.util.Vector
import kotlin.random.Random
import kotlinx.atomicfu.AtomicBoolean

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val array = atomicArrayOfNulls<Any>(10)
    private val lock = atomic(false)
    private var random = Random(222)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        if (q.size == 0) {
            return null
        }
        var result: E? = null;
        while (true) {
            if (tryLock()) {
                result = q.poll()
                combine()
                unlock()
                println(q.size)
                return result
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, 'O')) {
                while (true) {
                    if (array[counter].value != 'O') {
                        println(q.size)
                        return array[counter].value as E?
                    }
                    if (tryLock()) {
                        if (array[counter].value != 'O') {
                            unlock()
                            println(q.size)
                            return array[counter].value as E?
                        }
                        array[counter].value = null
                        result = q.poll()
                        combine()
                        unlock()
                        println(q.size)
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

            var counter = 0
            counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, 'E')) {
                while (true) {
                    if (array[counter].value != 'E') {
                        return array[counter].value as E?
                    }
                    if (tryLock()) {
                        if (array[counter].value != 'E') {
                            unlock()
                            return array[counter].value as E?
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

            var counter = 0
            counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, element)) {
                while (true) {
                    if (array[counter].value == null) {
                        return
                    }
                    if (tryLock()) {
                        if (array[counter].value == null) {
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
                when (task.value) {
                    'O' -> {
                        task.value = q.poll()
                    }
                    'E' -> {
                        task.value = q.peek()
                    }
                    else -> {
                        val elem = task.value
                        task.value = null
                        q.add(elem as E?)
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
}