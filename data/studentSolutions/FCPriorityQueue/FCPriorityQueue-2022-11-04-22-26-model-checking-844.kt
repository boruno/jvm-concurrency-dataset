import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val flatCombiningArraySize = 3
    private val flatCombiningArray = atomicArrayOfNulls<FcElement?>(flatCombiningArraySize)
    private val lock = ReentrantLock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var combined = false
        var combineIndex = 0

        while (true) {
            if (lock.tryLock()) {
                val result = q.poll()
                combine()
                lock.unlock()
                return result
            } else {
                if (!combined) {
                    combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                    val fcElement = FcElement(FcElementOperationType.Poll, null)

                    if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                        continue
                    else combined = true
                }

                if (waitCombiner(combineIndex)) {
                    val result = flatCombiningArray[combineIndex].value as E
                    flatCombiningArray[combineIndex].getAndSet(null)
                    return result
                } else continue
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var combined = false
        var combineIndex = 0

        while (true) {
            if (lock.tryLock()) {
                val result = q.peek()
                combine()
                lock.unlock()
                return result
            } else {
                if (!combined) {
                    combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                    val fcElement = FcElement(FcElementOperationType.Peek, null)

                    if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                        continue
                    else combined = true
                }

                if (waitCombiner(combineIndex)) {
                    val result = flatCombiningArray[combineIndex].value as E
                    flatCombiningArray[combineIndex].getAndSet(null)
                    return result
                } else continue
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var combined = false
        var combineIndex = 0

        while (true) {
            if (lock.tryLock()) {
                q.add(element)
                combine()
                lock.unlock()
                break
            } else {
                if (!combined) {
                    combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                    val fcElement = FcElement(FcElementOperationType.Add, element)

                    if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                        continue
                    else combined = true
                }

                if (waitCombiner(combineIndex)) {
                    flatCombiningArray[combineIndex].getAndSet(null)
                    break
                } else continue
            }
        }
    }

    private fun combine() {
        for (i in 0 until flatCombiningArraySize) {
            val fcElement = flatCombiningArray[i].value

            if (fcElement != null) {
                when (fcElement.operation) {
                    FcElementOperationType.Add -> {
                        q.add(fcElement.value as E)
                        flatCombiningArray[i].getAndSet(null)
                    }
                    FcElementOperationType.Poll -> {
                        // TODO: refactor this
                        flatCombiningArray[i].value?.value = q.poll()
                    }
                    FcElementOperationType.Peek -> {
                        // TODO: refactor this
                        flatCombiningArray[i].value?.value = q.poll()
                    }
                }
            }
        }
    }

    private fun waitCombiner(index: Int): Boolean {
        val fcElement = flatCombiningArray[index].value

        return if (fcElement != null) {
            when (fcElement.operation) {
                FcElementOperationType.Add -> {
                    fcElement.value == null
                }

                FcElementOperationType.Poll -> {
                    fcElement.value != null
                }

                FcElementOperationType.Peek -> {
                    fcElement.value != null
                }
            }
        } else false
    }
}

private data class FcElement(val operation: FcElementOperationType, var value: Any?)

private enum class FcElementOperationType {
    Add,
    Poll,
    Peek,
}