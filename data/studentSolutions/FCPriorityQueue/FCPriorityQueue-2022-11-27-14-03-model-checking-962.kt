import kotlinx.atomicfu.atomicArrayOfNulls
import kotlinx.atomicfu.locks.ReentrantLock
import java.util.*
import java.util.concurrent.ThreadLocalRandom

private const val LengthOfFlat = 10

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val flatArray = atomicArrayOfNulls<Flat<E>>(LengthOfFlat)
    private val lock: ReentrantLock = ReentrantLock()

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        var flatPlace: Int? = null

        while (true) {
            if (flatPlace != null) {
                val flat = flatArray[flatPlace].value ?: throw Exception();
                if (flat.type == OperationType.Done) {
                    clearFlatPlace(flatPlace)

                    return flat.value
                }
            }

            if (lock.tryLock()) {
                if (flatPlace != null) {
                    clearFlatPlace(flatPlace)
                }

                val answer = q.poll()

                processFlat()

                lock.unlock()

                return answer
            }

            if (flatPlace != null) {
                flatPlace = addToFlat(Flat<E>(OperationType.Poll, null))
            }
        }
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        var flatPlace: Int? = null

        while (true) {
            if (flatPlace != null) {
                val flat = flatArray[flatPlace].value ?: throw Exception();
                if (flat.type == OperationType.Done) {
                    clearFlatPlace(flatPlace)

                    return flat.value
                }
            }

            if (lock.tryLock()) {
                if (flatPlace != null) {
                    clearFlatPlace(flatPlace)
                }

                val answer = q.peek()

                processFlat()

                lock.unlock()

                return answer
            }

            if (flatPlace != null) {
                flatPlace = addToFlat(Flat<E>(OperationType.Peek, null))
            }
        }
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        var flatPlace: Int? = null

        while (true) {
            if (flatPlace != null) {
                val flat = flatArray[flatPlace].value ?: throw Exception();
                if (flat.type == OperationType.Done) {
                    clearFlatPlace(flatPlace)

                    return
                }
            }

            if (lock.tryLock()) {
                if (flatPlace != null) {
                    clearFlatPlace(flatPlace)
                }

                q.add(element)

                processFlat()

                lock.unlock()

                return
            }

            if (flatPlace != null) {
                flatPlace = addToFlat(Flat(OperationType.Add, element))
            }
        }
    }

    private fun doOperation(operation: () -> Any, element: E?): Any? {
        var flatPlace: Int? = null

        while (true) {
            if (flatPlace != null) {
                val flat = flatArray[flatPlace].value ?: throw Exception();
                if (flat.type == OperationType.Done) {
                    clearFlatPlace(flatPlace)

                    return flat.value
                }
            }

            if (lock.tryLock()) {
                if (flatPlace != null) {
                    clearFlatPlace(flatPlace)
                }

                val answer = operation.invoke()

                processFlat()

                lock.unlock()

                return answer
            }

            if (flatPlace != null) {
                flatPlace = addToFlat(Flat<E>(OperationType.Add, element))
            }
        }
    }

    private fun clearFlatPlace(place: Int) {
        flatArray[place].getAndSet(null)
    }

    private fun addToFlat(flat: Flat<E>): Int {
        while (true) {
            val pos: Int = ThreadLocalRandom.current().nextInt(LengthOfFlat)

            if (flatArray[pos].compareAndSet(null, flat)) {
                return pos
            }
        }
    }

    private fun processFlat() {
        for (i in 0 until LengthOfFlat) {
            val flat = flatArray[i].value ?: continue

            when (flat.type ) {
                OperationType.Poll -> {
                    val ans = q.poll()
                    flatArray[i].getAndSet(Flat(OperationType.Done, ans))
                }
                OperationType.Peek -> {
                    val ans = q.peek()
                    flatArray[i].getAndSet(Flat(OperationType.Done, ans))
                }
                OperationType.Add -> {
                    q.add(flat.value)
                    flatArray[i].getAndSet(Flat(OperationType.Done, null))
                }
                OperationType.Done -> {}
            }
        }
    }
}

class Flat<E>(val type: OperationType, val value: E?)

enum class OperationType {
    Poll,
    Peek,
    Add,
    Done
}