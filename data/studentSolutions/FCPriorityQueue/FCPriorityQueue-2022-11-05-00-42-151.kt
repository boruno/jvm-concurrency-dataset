import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val flatCombiningArraySize = 3
    private val flatCombiningArray = atomicArrayOfNulls<FcElement<E>?>(flatCombiningArraySize)
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
                if (combined) {
                    val fcElement: FcElement<E> = flatCombiningArray[combineIndex].getAndSet(null) ?: throw Exception()

                    if (fcElement.element != null) {
                        combine()
                        unlock()
                        return fcElement.element
                    }
                }

                // If combine with someone was not performed (element still 'null')
                // or it is the first try to get lock, we just poll.
                val result = q.poll()

                combine()
                unlock()
                return result
            }

            if (!combined) {
                combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                val fcElement = FcElement<E>(FcElementOperationType.Poll, null)

                if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                    continue

                combined = true
            } else if (waitCombiner(combineIndex)) {
                val result: FcElement<E> = flatCombiningArray[combineIndex].getAndSet(null) ?: throw Exception()
                return result.element
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
            if (tryLock()) {
                if (combined) {
                    val fcElement: FcElement<E> = flatCombiningArray[combineIndex].getAndSet(null) ?: throw Exception()

                    if (fcElement.element != null) {
                        combine()
                        unlock()
                        return fcElement.element
                    }
                }

                // If combine with someone was not performed (element still 'null')
                // or it is the first try to get lock, we just peek.
                val result = q.peek()

                combine()
                unlock()
                return result
            }

            if (!combined) {
                combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                val fcElement = FcElement<E>(FcElementOperationType.Peek, null)

                if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                    continue

                combined = true
            } else if (waitCombiner(combineIndex)) {
                val result: FcElement<E> = flatCombiningArray[combineIndex].getAndSet(null) ?: throw Exception()
                return result.element
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
            if (tryLock()) {
                if (combined) {
                    val fcElement: FcElement<E> = flatCombiningArray[combineIndex].getAndSet(null) ?: throw Exception()

                    if (fcElement.element == null) {
                        combine()
                        unlock()
                        return
                    }
                }

                // If combine with someone was not performed (element still 'not null')
                // or it is the first try to get lock, we just add.
                q.add(element);

                combine()
                unlock()
                return
            }

            if (!combined) {
                combineIndex = ThreadLocalRandom.current().nextInt(0, flatCombiningArraySize)
                val fcElement = FcElement(FcElementOperationType.Add, element)

                if (!flatCombiningArray[combineIndex].compareAndSet(null, fcElement))
                    continue

                combined = true
            } else if (waitCombiner(combineIndex)) {
                flatCombiningArray[combineIndex].getAndSet(null) ?: throw Exception()
                return
            }
        }
    }

    private fun combine() {
        for (i in 0 until flatCombiningArraySize) {
            val fcElement = flatCombiningArray[i].value

            if (fcElement != null) {
                when (fcElement.operation) {
                    FcElementOperationType.Add -> {
                        q.add(fcElement.element)
                        flatCombiningArray[i].value?.element = null
                    }
                    FcElementOperationType.Poll -> {
                        flatCombiningArray[i].value?.element = q.poll()
                    }
                    FcElementOperationType.Peek -> {
                        flatCombiningArray[i].value?.element = q.peek()
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
                    fcElement.element == null
                }

                FcElementOperationType.Poll -> {
                    fcElement.element != null
                }

                FcElementOperationType.Peek -> {
                    fcElement.element != null
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

private data class FcElement<E>(val operation: FcElementOperationType, var element: E?)

private enum class FcElementOperationType {
    Add,
    Poll,
    Peek,
}