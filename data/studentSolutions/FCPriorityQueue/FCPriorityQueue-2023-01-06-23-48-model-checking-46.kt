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
        print("POLL: ")
        var result: E? = null;
        while (true) {
            if (tryLock()) {
                result = q.poll()
                combine()
                unlock()
                println(result)
                return result
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, 'O')) {
                while (true) {
                    if (array[counter].value != 'O') {
                        println(result)
                        return array[counter].value as E?
                    }
                    if (tryLock()) {
                        array[counter].value = null
                        result = q.poll()
                        combine()
                        unlock()
                        println(result)
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
        print("PEEK: ")
        var result: E? = null;
        while (true) {
            if (tryLock()) {
                result = q.peek()
                combine()
                unlock()
                println(result)
                return result
            }

            var counter = 0
            counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, 'E')) {
                while (true) {
                    if (array[counter].value != 'E') {
                        println(result)
                        return array[counter].value as E?
                    }
                    if (tryLock()) {
                        array[counter].value = null
                        result = q.peek()
                        combine()
                        unlock()
                        println(result)
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
        print("ADD: ")
        println(element)
        while (true) {
            if (tryLock()) {
                q.add(element)
                combine()
                unlock()
            }

            var counter = 0
            counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, element)) {
                while (true) {
                    if (array[counter].value == null) {
                        return
                    }
                    if (tryLock()) {
                        array[counter].value = null
                        q.add(element)
                        combine()
                        unlock()
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
                        q.add(task.value as E?)
                        task.value = null
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