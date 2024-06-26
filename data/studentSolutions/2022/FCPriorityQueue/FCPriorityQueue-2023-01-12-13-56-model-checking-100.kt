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
                var answer: E? = null
                if (idx != null) {
                    val status = array[idx].value!!.status.value
                    if (status == Status.TODO) {
                        answer = q.poll()
                    } else if (status == Status.DONE) {
                        answer = array[idx].value!!._value.value as E
                    }
                    array[idx].value = null
                } else {
                    answer = q.poll()
                }
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
                        array[idx].value = null
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
        var idx: Int? = null

        while (true) {
            if (trylock()) {
                var answer: E? = null
                if (idx != null) {
                    if (array[idx].value!!.status.value == Status.TODO) {
                        answer = q.peek()
                    } else if (array[idx].value!!.status.value == Status.DONE) {
                        answer = array[idx].value!!._value.value as E
                    }
                    array[idx].value = null
                } else {
                    answer = q.peek()
                }
                clearArray()
                unlock()
                return answer
            } else {
                if (idx == null) {
                    val tmp = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
                    if (array[tmp].value == null) {
                        if (array[tmp].compareAndSet(null, Task("PEEK", null))) {
                            idx = tmp
                        }
                    }
                } else {
                    val status = array[idx].value!!.status.value
                    if (status == Status.DONE) {
                        val answer = array[idx].value!!._value.value
                        array[idx].value = null
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
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var idx: Int? = null

        while (true) {
            if (trylock()) {
                if (idx != null) {
                    if (array[idx].value!!.status.value == Status.TODO) {
                        q.add(element)
                    }
                    array[idx].value = null
                } else {
                    q.add(element)
                }
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
                } else if (task._command.value == "DELETE"){
                    val answer = q.poll()
                    if (answer == null) {
                        task.status.value = Status.DECLAINED
                    } else {
                        task._value.value = answer
                        task.status.value = Status.DONE
                    }
                } else {
                    val answer = q.peek()
                    if (answer == null) {
                        task.status.value = Status.DECLAINED
                    } else {
                        task._value.value = answer
                        task.status.value = Status.DONE
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