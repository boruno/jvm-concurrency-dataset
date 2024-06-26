import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean

class FCPriorityQueue<E : Comparable<E>> {
    private val arraySize = 3
    private val q = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Any?>(arraySize)
    private val lock = Lock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        outerloop@ while (true) {
            if (lock.tryLock()) {
                val result = q.poll()
                for (i in 0 until arraySize) {
                    val valueI = fcArray[i].value
                    if (valueI != null) {
                        val helpNode = valueI as Node<E>
                        if (helpNode.type == "add") {
                            q.add(helpNode.element)
                            helpNode.executed = true
                        } else if (helpNode.type == "peek") {
                            helpNode.element = q.peek()
                            helpNode.executed = true
                        } else {
                            helpNode.element = q.poll()
                            helpNode.executed = true
                        }
                    }
                }
                lock.unLock()
                return result
            }

            val random = ThreadLocalRandom.current().nextInt(arraySize)
            val node = Node("poll", null)
            while (true) {
                if (fcArray[random].compareAndSet(null, node)) {
                    break
                }
            }

            while (true) {
                if (!lock.isLocked()) {
                    if ((fcArray[random].value as Node<E>).executed) {
                        return (fcArray[random].value as Node<E>).element
                    } else {
                        continue@outerloop
                    }
                }
                if ((fcArray[random].value as Node<E>).executed) {
                    return (fcArray[random].value as Node<E>).element
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        outerloop@ while (true) {
            if (lock.tryLock()) {
                val result = q.peek()
                for (i in 0 until arraySize) {
                    val valueI = fcArray[i].value
                    if (valueI != null) {
                        val helpNode = valueI as Node<E>
                        if (helpNode.type == "add") {
                            q.add(helpNode.element)
                            helpNode.executed = true
                        } else if (helpNode.type == "peek") {
                            helpNode.element = q.peek()
                            helpNode.executed = true
                        } else {
                            helpNode.element = q.poll()
                            helpNode.executed = true
                        }
                    }
                }
                lock.unLock()
                return result
            }

            val random = ThreadLocalRandom.current().nextInt(arraySize)
            val node = Node("peek", null)
            while (true) {
                if (fcArray[random].compareAndSet(null, node)) {
                    break
                }
            }

            while (true) {
                if (!lock.isLocked()) {
                    if ((fcArray[random].value as Node<E>).executed) {
                        return (fcArray[random].value as Node<E>).element
                    } else {
                        continue@outerloop
                    }
                }
                if ((fcArray[random].value as Node<E>).executed) {
                    return (fcArray[random].value as Node<E>).element
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        outerloop@ while (true) {
            if (lock.tryLock()) {
                q.add(element)
                for (i in 0 until arraySize) {
                    val valueI = fcArray[i].value
                    if (valueI != null) {
                        val helpNode = valueI as Node<E>
                        if (helpNode.type == "add") {
                            q.add(helpNode.element)
                            helpNode.executed = true
                        } else if (helpNode.type == "peek") {
                            helpNode.element = q.peek()
                            helpNode.executed = true
                        } else {
                            helpNode.element = q.poll()
                            helpNode.executed = true
                        }
                    }
                }
                lock.unLock()
                return
            }

            val random = ThreadLocalRandom.current().nextInt(arraySize)
            val node = Node("add", element)
            while (true) {
                if (fcArray[random].compareAndSet(null, node)) {
                    break
                }
            }

            while (true) {
                if (!lock.isLocked()) {
                    if ((fcArray[random].value as Node<E>).executed) {
                        return
                    } else {
                        continue@outerloop
                    }
                }
                if ((fcArray[random].value as Node<E>).executed) {
                    return
                }
            }
        }
    }

    private class Node<E>(val type: String, var element: E?) {
        var executed = false
    }
    private class Lock {
        private val locked = AtomicBoolean(false)

        fun tryLock() = locked.compareAndSet(false, true)
        fun unLock() {
            locked.set(false)
        }
        fun isLocked() = locked.get()
    }
}