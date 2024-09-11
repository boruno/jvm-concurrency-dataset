import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val flatCombiningArraySize = 3
    private val flatCombiningArray = atomicArrayOfNulls<FcElement?>(flatCombiningArraySize)
    private val locked = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var combined = false
        var combineIndex = 0

        while (true) {
            if (tryLock()) {
                val result = q.poll()

                if (combined) {
                    flatCombiningArray[combineIndex].getAndSet(null)
                }

                combine()
                unlock()
                return result
            } else if (!combined) {
                combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                val fcElement = FcElement(FcElementOperationType.Poll, null)

                if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                    continue
                else combined = true
            } else if (waitCombiner(combineIndex)) {
                val result = flatCombiningArray[combineIndex].value as E
                flatCombiningArray[combineIndex].getAndSet(null)
                return result
            } else continue
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
            if (tryLock()) {
                val result = q.peek()

                if (combined) {
                    flatCombiningArray[combineIndex].getAndSet(null)
                }

                combine()
                unlock()
                return result
            } else if (!combined) {
                combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                val fcElement = FcElement(FcElementOperationType.Peek, null)

                if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                    continue
                else combined = true
            } else if (waitCombiner(combineIndex)) {
                val result = flatCombiningArray[combineIndex].value as E
                flatCombiningArray[combineIndex].getAndSet(null)
                return result
            } else continue
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var combined = false
        var combineIndex = 0

        while (true) {
            if (tryLock()) {
                q.add(element)

                if (combined) {
                    flatCombiningArray[combineIndex].getAndSet(null)
                }

                combine()
                unlock()
                break
            } else if (!combined) {
                combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                val fcElement = FcElement(FcElementOperationType.Add, element)

                if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                    continue
                else combined = true
            } else if (waitCombiner(combineIndex)) {
                flatCombiningArray[combineIndex].getAndSet(null)
                break
            } else continue
        }
    }

    private fun combine() {
        for (i in 0 until flatCombiningArraySize) {
            val fcElement = flatCombiningArray[i].value

            if (fcElement != null) {
                when (fcElement.operation) {
                    FcElementOperationType.Add -> {
                        q.add(fcElement.value as E)
                        fcElement.value = null
                    }
                    FcElementOperationType.Poll -> {
                        // TODO: refactor this
                        flatCombiningArray[i].value?.value = q.poll()
                    }
                    FcElementOperationType.Peek -> {
                        // TODO: refactor this
                        flatCombiningArray[i].value?.value = q.peek()
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

    private fun tryLock(): Boolean {
        return locked.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        locked.getAndSet(false)
    }
}

private data class FcElement(val operation: FcElementOperationType, var value: Any?)

private enum class FcElementOperationType {
    Add,
    Poll,
    Peek,
}