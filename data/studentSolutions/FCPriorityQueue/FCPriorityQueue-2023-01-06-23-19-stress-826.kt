import java.util.PriorityQueue
import kotlinx.atomicfu.*
import java.util.concurrent.locks.ReentrantLock
import java.util.Vector
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val array = atomicArrayOfNulls<Any>(10)
    private var lock: ReentrantLock = ReentrantLock()
    private var random = Random(222)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var result: E? = null;
        while (true) {
            if (lock.tryLock()) {
                print("Q val bef")
                print(q.peek())
                result = q.poll()
                print("Q val aft")
                print(q.peek())
                combine()
                lock.unlock()
                return result
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, 'O')) {
                while (true) {
                    if (array[counter].value != 'O') {
                        return array[counter].value as E?
                    }
                    if (lock.tryLock()) {
                        array[counter].value = null
                        print("Q val bef")
                        print(q.peek())
                        result = q.poll()
                        print("Q val aft")
                        print(q.peek())
                        combine()
                        lock.unlock()
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
            if (lock.tryLock()) {
                result = q.peek()
                combine()
                lock.unlock()
                return result
            }

            var counter = 0
            counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, 'E')) {
                while (true) {
                    if (array[counter].value != 'E') {
                        return array[counter].value as E?
                    }
                    if (lock.tryLock()) {
                        array[counter].value = null
                        result = q.peek()
                        combine()
                        lock.unlock()
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
            if (lock.tryLock()) {
                q.add(element)
                combine()
                lock.unlock()
            }

            var counter = 0
            counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, element)) {
                while (true) {
                    if (array[counter].value == null) {
                        return
                    }
                    if (lock.tryLock()) {
                        array[counter].value = null
                        q.add(element)
                        combine()
                        lock.unlock()
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
}