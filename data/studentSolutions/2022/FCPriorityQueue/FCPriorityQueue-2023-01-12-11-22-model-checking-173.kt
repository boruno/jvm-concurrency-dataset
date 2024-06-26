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
                if (idx == null) {
                    val tmp = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                    if (array[tmp].value == null) {
                        if (array[tmp].compareAndSet(null, Task("DELETE", null))) {
                            idx = tmp
                        }
                    }
                } else {
                    val status = array[idx].value!!.status.value
                    if (status == Status.DONE) {
                        val answer = array[idx].value!!._value.value
                        return answer as E?
                    } else if (status == Status.DECLAINED) {
                        array[idx].value = null
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
                if (idx == null) {
                    val tmp = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                    if (array[tmp].value == null) {
                        if (array[tmp].compareAndSet(null, Task("PUSH", element))) {
                            idx = tmp
                        }
                    }
                } else {
                    if (array[idx].value!!.status.value == Status.DONE) {
                        array[idx].value = null
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
            }
        }
    }

    fun trylock() = lock.compareAndSet(expect = false, update = true)

    fun unlock() {
        lock.value = false
    }
}

class Task(val command: String, val number: Any?) {
    val status: AtomicRef<Status> = atomic(Status.TODO)
    val _command: AtomicRef<String> = atomic(command)
    val _value: AtomicRef<Any?> = atomic(number)
}

enum class Status {
    DONE,
    DECLAINED,
    TODO
}