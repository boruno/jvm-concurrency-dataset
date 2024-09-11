import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Random

class FCPriorityQueue<E : Comparable<E>> {
    private val arraySize = 3
    private val q = PriorityQueue<E>()
    private val fcArray = atomicArrayOfNulls<Any?>(arraySize)
    private val lockQueue = Lock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        outerloop@ while (true) {
            if (lockQueue.tryLock()) {
                val result = q.poll()
                helping()

                lockQueue.unLock()
                return result
            }

            val inputNode = Node("poll", null)
            val cell = ThreadLocalRandom.current().nextInt(0, arraySize)

            while (true) {
                if (fcArray[cell].compareAndSet(null, inputNode)) break
            }

            while (true) {
                if (!lockQueue.isLocked()) {
                    if (fcArray[cell].compareAndSet(inputNode, null)) {
                        continue@outerloop
                    } else {
                        while (true) {
                            val value = (fcArray[cell].value ?: continue@outerloop) as Node<E>
                            if (fcArray[cell].compareAndSet(value, null)) {
                                if (value.executed) {
                                    return value.element
                                }
                                continue@outerloop
                            }
                        }
                    }
                }
                if (!fcArray[cell].compareAndSet(inputNode, inputNode)) {
                    while (true) {
                        val value = (fcArray[cell].value ?: continue@outerloop) as Node<E>
                        if (fcArray[cell].compareAndSet(value, null)) {
                            if (value.executed) {
                                return value.element
                            }
                            continue@outerloop
                        }
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
        outerloop@ while (true) {
            if (lockQueue.tryLock()) {
                val result = q.peek()
                helping()

                lockQueue.unLock()
                return result
            }

            val inputNode = Node("peek", null)
            val cell = ThreadLocalRandom.current().nextInt(0, arraySize)

            while (true) {
                if (fcArray[cell].compareAndSet(null, inputNode)) break
            }

            while (true) {
                if (!lockQueue.isLocked()) {
                    if (fcArray[cell].compareAndSet(inputNode, null)) {
                        continue@outerloop
                    } else {
                        while (true) {
                            val value = (fcArray[cell].value ?: continue@outerloop) as Node<E>
                            if (fcArray[cell].compareAndSet(value, null)) {
                                if (value.executed) {
                                    return value.element
                                }
                                continue@outerloop
                            }
                        }
                    }
                }
                if (!fcArray[cell].compareAndSet(inputNode, inputNode)) {
                    while (true) {
                        val value = (fcArray[cell].value ?: continue@outerloop) as Node<E>
                        if (fcArray[cell].compareAndSet(value, null)) {
                            if (value.executed) {
                                return value.element
                            }
                            continue@outerloop
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        outerloop@ while (true) {
            if (lockQueue.tryLock()) {
                q.add(element)
                helping()

                lockQueue.unLock()
                return
            }

            val inputNode = Node("add", element)
            val cell = ThreadLocalRandom.current().nextInt(0, arraySize)

            while (true) {
                if (fcArray[cell].compareAndSet(null, inputNode)) break
            }

            while (true) {
                if (!lockQueue.isLocked()) {
                    if (fcArray[cell].compareAndSet(inputNode, null)) {
                        continue@outerloop
                    } else {
                        while (true) {
                            val value = (fcArray[cell].value ?: continue@outerloop) as Node<E>
                            if (fcArray[cell].compareAndSet(value, null)) {
                                if (value.executed) {
                                    return
                                }
                                continue@outerloop
                            }
                        }
                    }
                }
                if (!fcArray[cell].compareAndSet(inputNode, null)) {
                    while (true) {
                        val value = (fcArray[cell].value ?: continue@outerloop) as Node<E>
                        if (fcArray[cell].compareAndSet(value, null)) {
                            if (value.executed) {
                                return
                            }
                            continue@outerloop
                        }
                    }
                }
            }
        }
    }

    private fun helping() {
        for (i in 0 until arraySize) {
            // if cell is null then continue
            val currentCell = (fcArray[i].value ?: continue) as Node<E>
            // or if this cell had already executed
            if (currentCell.executed) continue

            if (currentCell.type == "add") {
                q.add(currentCell.element)
                val insertNode = Node("add", null)
                insertNode.executed = true
                if (!fcArray[i].compareAndSet(currentCell, insertNode)) {
                    q.remove(currentCell.element)
                }
            }

            if (currentCell.type == "peek") {
                val result = q.peek()
                val insertNode = Node("peek", result)
                insertNode.executed = true
                fcArray[i].compareAndSet(currentCell, insertNode)
            }

            if (currentCell.type == "poll") {
                val result = q.poll()
                val insertNode = Node("poll", result)
                insertNode.executed = true
                if (!fcArray[i].compareAndSet(currentCell, insertNode)) {
                    q.add(result)
                }
            }
        }
    }

    private class Node<E>(_type: String, _element: E?) {
        val type: String = _type
        var element = _element
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