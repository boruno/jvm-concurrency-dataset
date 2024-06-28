import java.util.PriorityQueue
import kotlinx.atomicfu.*
import kotlin.random.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val array = atomicArrayOfNulls<Operation>(10)
    private val globalLock = MyLock()
    private val arrayLock = MyLock()
    private var random = Random(222)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        val result: E?
        while (true) {
            if (globalLock.tryLock()) {
                result = q.poll()
                //combine()
                globalLock.unlock()
                return result
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, Operation('O', null, false, MyLock()))) {
                while (true) {
                    if (array[counter].value?.finished == true) {
                        result = array[counter].value?.value as E?
                        array[counter].value = null
                        return result
                    }
                    if (globalLock.tryLock()) {
                        result = if (array[counter].value?.finished == false) {
                            q.poll()
                        } else {
                            array[counter].value?.value as E?
                        }
                        array[counter].value = null
                        //combine()
                        globalLock.unlock()
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
        val result: E?
        while (true) {
            if (globalLock.tryLock()) {
                result = q.peek()
                //combine()
                globalLock.unlock()
                return result
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, Operation('E', null, false, MyLock()))) {
                while (true) {
                    if (array[counter].value?.finished == true) {
                        result = array[counter].value?.value as E?
                        array[counter].value = null
                        return result
                    }
                    if (globalLock.tryLock()) {
                        result = if (array[counter].value?.finished == false) {
                            q.peek()
                        } else {
                            array[counter].value?.value as E?
                        }
                        array[counter].value = null
                        //combine()
                        globalLock.unlock()
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
            if (globalLock.tryLock()) {
                q.add(element)
                combine()
                globalLock.unlock()
                return
            }

            val counter = random.nextInt(0, 10)
            if (array[counter].compareAndSet(null, Operation('A', element, false, MyLock()))) {
                while (true) {
                    if (array[counter].value?.finished == true) {
                        array[counter].value = null
                        return
                    }
                    if (globalLock.tryLock()) {
                        if (array[counter].value?.finished == false) {
                            q.add(element)
                        }
                        array[counter].value = null
                        //combine()
                        globalLock.unlock()
                        return
                    }
                }
            }
        }
    }

    private fun combine() {
        for (i in 0..9) {
            val task = array[i].value
            if (task != null) {
                if (!task.finished) {
                    if (task.operation == 'O') {
                        array[i].value?.value = q.poll()
                        array[i].value?.finished = true
                    }
                    if (task.operation == 'E') {
                        array[i].value?.value = q.poll()
                        array[i].value?.finished = true
                    }
                    if (task.operation == 'A') {
                        q.add(task.value as E?)
                        array[i].value?.finished = true
                    }
                }
            }
        }
//        for (i in 0..9) {
//            if (array[i].value != null) {
//                if (array[i].value?.lock?.tryLock() == true) {
//                    val task = array[i]
//                    if (task.value != null) {
//                        when (task.value!!.operation) {
//                            'O' -> {
//
//                                if (task.value?.finished == false) {
//                                    task.value?.finished = true
//                                    task.value?.value = q.poll()
//                                }
//                            }
//
//                            'E' -> {
//                                if (task.value?.finished == false) {
//                                    task.value?.finished = true
//                                    task.value?.value = q.peek()
//                                }
//                            }
//
//                            'A' -> {
//                                if (task.value?.finished == false) {
//                                    task.value?.finished = true
//                                    q.add(task.value!!.value as E?)
//                                }
//                            }
//                        }
//                    }
//                    array[i].value?.lock?.unlock()
//                }
//            }
//        }
    }

    data class Operation(var operation: Char?, var value: Any?, var finished: Boolean, var lock: MyLock)
}

class MyLock {
    private val lock = atomic(initial = false)

    fun tryLock(): Boolean {
        return lock.compareAndSet(expect = false, update = true)
    }

    fun unlock() {
        lock.value = false
    }

    fun isLocked(): Boolean {
        return lock.value
    }
}

