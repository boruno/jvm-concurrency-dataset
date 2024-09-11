import kotlinx.atomicfu.AtomicArray
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Random
import kotlin.reflect.typeOf

class FCPriorityQueue<E : Comparable<E>> {
    private val arraySize = 9
    private val constSize = 3
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
            if (q.isEmpty()) {
                return null
            }
            if (lock.tryLock()) {
                for (i in 0 until arraySize) {
                    val valueI = fcArray[i].value
                    if (valueI != null) {
                        val helpNode = valueI as Node<E>
                        if (helpNode.executed) {
                            continue
                        }
                        if (helpNode.type == "add") {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                q.add(helpNode.element)
                                helpNode.executed = true
                            }
                            helpNode.lock.unLock()
                            // val newNode = Node("add", helpNode.element)
                            // fcArray[i].getAndSet(newNode)
                        } else if (helpNode.type == "peek") {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                helpNode.executed = true
                                helpNode.element = q.peek()
                            }
                            helpNode.lock.unLock()
                            //val newNode = Node("peek", q.peek())

                            // fcArray[i].compareAndSet(helpNode, newNode)
                        } else {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                helpNode.executed = true
                                helpNode.element = q.poll()
                            }
                            helpNode.lock.unLock()
                            // val newNode = Node("poll", q.poll())

                            // newNode.executed = true
                            // fcArray[i].getAndSet(newNode)
                        }
                    }
                }
                val result = q.poll()
                lock.unLock()
                return result
            }

            // var random : Int
            var random = ThreadLocalRandom.current().nextInt(arraySize / constSize)
            val node = Node<E>("poll", null)
            arrayLoop@ while (true) {
                // random = Random().nextInt(arraySize)
                for (i in 0 .. 1000) {
                    if (fcArray[random].compareAndSet(null, node)) {
                        break@arrayLoop
                    }
                }

                for (i in 1 until constSize) {
                    random += i * constSize
                    if (fcArray[random].compareAndSet(null, node)) {
                        break@arrayLoop
                    }
                }
                random = ThreadLocalRandom.current().nextInt(arraySize / constSize)
                /*
                for (i in 0 until arraySize) {
                    if (fcArray[i].compareAndSet(null, node)) {
                        random = i
                        break@arrayLoop
                    }
                }

                 */
            }

            while (true) {
                if (!lock.isLocked()) {
                    val out = fcArray[random].getAndSet(null) as Node<E>
                    while (true) {
                        if (!out.lock.isLocked()) {
                            break
                        }
                    }
                    if (out.executed) {
                        return out.element
                    }
                    continue@outerloop
                }

                if ((fcArray[random].value as Node<E>).executed) {
                    val out = fcArray[random].getAndSet(null) as Node<E>
                    return out.element
                }
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        if (q.isEmpty()) {
            return null
        }
        outerloop@ while (true) {
            if (lock.tryLock()) {
                for (i in 0 until arraySize) {
                    val valueI = fcArray[i].value
                    if (valueI != null) {
                        val helpNode = valueI as Node<E>
                        if (helpNode.executed) {
                            continue
                        }
                        if (helpNode.type == "add") {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                q.add(helpNode.element)
                                helpNode.executed = true
                            }
                            helpNode.lock.unLock()
                            // val newNode = Node("add", helpNode.element)
                            // fcArray[i].getAndSet(newNode)
                        } else if (helpNode.type == "peek") {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                helpNode.executed = true
                                helpNode.element = q.peek()
                            }
                            helpNode.lock.unLock()
                            //val newNode = Node("peek", q.peek())

                            // fcArray[i].compareAndSet(helpNode, newNode)
                        } else {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                helpNode.executed = true
                                helpNode.element = q.poll()
                            }
                            helpNode.lock.unLock()
                            // val newNode = Node("poll", q.poll())

                            // newNode.executed = true
                            // fcArray[i].getAndSet(newNode)
                        }
                    }
                }
                val result = q.peek()
                lock.unLock()
                return result
            }

            //var random : Int
            var random = ThreadLocalRandom.current().nextInt(arraySize / constSize)
            val node = Node<E>("peek", null)
            arrayLoop@ while (true) {
                /*
                for (i in 0 until arraySize) {
                    if (fcArray[random].compareAndSet(null, node)) {
                        break@arrayLoop
                    }
                    random++
                    if (random == arraySize) {
                        random = 0
                    }
                }
                */
                // random = Random().nextInt(arraySize)
                for (i in 0 .. 1000) {
                    if (fcArray[random].compareAndSet(null, node)) {
                        break@arrayLoop
                    }
                }

                for (i in 1 until constSize) {
                    random += i * constSize
                    if (fcArray[random].compareAndSet(null, node)) {
                        break@arrayLoop
                    }
                }
                random = ThreadLocalRandom.current().nextInt(arraySize / constSize)
/*
                for (i in 0 until arraySize) {
                    if (fcArray[i].compareAndSet(null, node)) {
                        random = i
                        break@arrayLoop
                    }
                }

 */
            }


            while (true) {
                if (!lock.isLocked()) {
                    val out = fcArray[random].getAndSet(null) as Node<E>
                    while (true) {
                        if (!out.lock.isLocked()) {
                            break
                        }
                    }
                    if (out.executed) {
                        return out.element
                    }
                    continue@outerloop
                }
                if ((fcArray[random].value as Node<E>).executed) {
                    val out = fcArray[random].getAndSet(null) as Node<E>
                    return out.element
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
                for (i in 0 until arraySize) {
                    val valueI = fcArray[i].value
                    if (valueI != null) {
                        val helpNode = valueI as Node<E>
                        if (helpNode.executed) {
                            continue
                        }
                        if (helpNode.type == "add") {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                q.add(helpNode.element)
                                helpNode.executed = true
                            }
                            helpNode.lock.unLock()
                            // val newNode = Node("add", helpNode.element)
                            // fcArray[i].getAndSet(newNode)
                        } else if (helpNode.type == "peek") {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                helpNode.executed = true
                                helpNode.element = q.peek()
                            }
                            helpNode.lock.unLock()
                            //val newNode = Node("peek", q.peek())

                            // fcArray[i].compareAndSet(helpNode, newNode)
                        } else {
                            helpNode.lock.tryLock()
                            if (fcArray[i].compareAndSet(helpNode, helpNode)) {
                                helpNode.executed = true
                                helpNode.element = q.poll()
                            }
                            helpNode.lock.unLock()
                            // val newNode = Node("poll", q.poll())

                            // newNode.executed = true
                            // fcArray[i].getAndSet(newNode)
                        }
                    }
                }
                q.add(element)
                lock.unLock()
                return
            }

            //var random : Int
            var random = ThreadLocalRandom.current().nextInt(arraySize / constSize)
            val node = Node<E>("add", element)
            arrayLoop@ while (true) {
                //random = Random().nextInt(arraySize)
                for (i in 0 .. 1000) {
                    if (fcArray[random].compareAndSet(null, node)) {
                        break@arrayLoop
                    }
                }

                for (i in 1 until constSize) {
                    random += i * constSize
                    if (fcArray[random].compareAndSet(null, node)) {
                        break@arrayLoop
                    }
                }
                random = ThreadLocalRandom.current().nextInt(arraySize / constSize)
/*
                for (i in 0 until arraySize) {
                    if (fcArray[i].compareAndSet(null, node)) {
                        random = i
                        break@arrayLoop
                    }
                }

 */
            }

            while (true) {
                if (!lock.isLocked()) {
                    val out = fcArray[random].getAndSet(null) as Node<E>
                    if (out.executed) {
                        return
                    }
                    continue@outerloop
                }
                if ((fcArray[random].value as Node<E>).executed) {
                    fcArray[random].getAndSet(null)
                    return
                }
            }
        }
    }

    private class Node<E>(_type: String, _element: E?) {
        val type: String = _type
        var element: E? = _element
        var executed = false
        var lock = Lock()
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