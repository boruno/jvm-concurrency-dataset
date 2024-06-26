import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val ARRAY_SIZE = 10
    private val q = PriorityQueue<E>()
    private val array = atomicArrayOfNulls<Task>(ARRAY_SIZE)
    private val lock = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var inArray = false
        var idx: Int? = null

        while (true) {
            if (trylock()) {
                if (idx != null) {
                    array[idx].value = null
                }
                val answer = q.poll()
                clearArray()
                unlock()
                return answer
            } else {
                if (!inArray) {
                    val tmp = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                    if (array[tmp].value == null) {
                        idx = tmp
                        array[idx].value = Task("DELETE", null)
                        inArray = true
                    }
                } else {
                    if (array[idx!!].value!!.status.value == Status.DONE) {
                        return array[idx].value!!.value as E?
                    } else if (array[idx].value!!.status.value == Status.DECLAINED) {
                        return null
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
        return q.peek()
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var inArray = false
        var idx: Int? = null

        while (true) {
            if (trylock()) {
                if (idx != null) {
                    array[idx].value = null
                }
                q.add(element)
                clearArray()
                unlock()
                return
            } else {
                if (!inArray) {
                    val tmp = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                    if (array[tmp].value == null) {
                        idx = tmp
                        array[idx].value = Task("PUSH", element)
                        inArray = true
                    }
                } else {
                    if (array[idx!!].value!!.status.value == Status.DONE) {
                        return
                    }
                }
            }
        }
    }

    fun clearArray() {
        for (i in 0..9) {
            val task = array[i].value
            if (task != null) {
                if (task._command.value == "PUSH") {
                    q.add(task._value.value!! as E)
                    task.status.value = Status.DONE
                } else {
                    if (peek() == null) {
                        task.status.value = Status.DECLAINED
                    } else {
                        val answer = q.poll()
                        task.status.value = Status.DONE
                        task._value.value = answer
                    }
                }
                array[i].value = null
            }
        }
    }

    fun trylock() = lock.compareAndSet(expect = false, update = true)

    fun unlock() {
        lock.value = false
    }
}

class Task(val command: String, val value: Any?) {
    val status: AtomicRef<Status> = atomic(Status.TODO)
    val _command: AtomicRef<String> = atomic(command)
    val _value: AtomicRef<Any?> = atomic(value)
}

enum class Status {
    DONE,
    DECLAINED,
    TODO
}