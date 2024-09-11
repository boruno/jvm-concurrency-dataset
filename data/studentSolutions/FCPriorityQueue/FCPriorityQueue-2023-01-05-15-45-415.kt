import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.atomicArrayOfNulls
import java.util.*
import java.util.concurrent.ThreadLocalRandom

private const val LengthOfFlat = 20

class FCPriorityQueue<E : Comparable<E>> {
    private val q = PriorityQueue<E>()
    private val flatArray = atomicArrayOfNulls<Flat<E>>(LengthOfFlat)
    private val lock = atomic(false)

    /**
     * Retrieves the element with the highest priority
     * and returns it as the result of this function;
     * returns `null` if the queue is empty.
     */
    fun poll(): E? {
        return doOperation(OperationType.Poll)
    }

    /**
     * Returns the element with the highest priority
     * or `null` if the queue is empty.
     */
    fun peek(): E? {
        return doOperation(OperationType.Peek)
    }

    /**
     * Adds the specified element to the queue.
     */
    fun add(element: E) {
        doOperation(OperationType.Add, element)
    }

    private fun doOperation(type: OperationType, value: E? = null): E? {
        val flatIndex = addToFlat(Flat(type, value = value))
        while (true) {
            val currentFlat = flatArray[flatIndex].value!!
            if (currentFlat.status == Status.Done) {
                val answer = currentFlat.value
                clearFlatPlace(flatIndex)

                return answer
            }

            if (tryLock()) {
                val answer = doQueueOperation(currentFlat)
                clearFlatPlace(flatIndex)
                processFlat()
                unlock()

                return answer
            }
        }
    }

    private fun clearFlatPlace(place: Int) {
        flatArray[place].getAndSet(null)
    }

    private fun addToFlat(flat: Flat<E>): Int {
        while(true) {
            val pos: Int = ThreadLocalRandom.current().nextInt(LengthOfFlat)

            if (flatArray[pos].compareAndSet(null, flat)) {
                return pos
            }
        }
    }

    private fun processFlat() {
        for (i in 0 until LengthOfFlat) {
            val flat = flatArray[i].value ?: continue

            if (flat.status == Status.Done) {
                continue
            }

            val ans = doQueueOperation(flat)
            flatArray[i].getAndSet(Flat(flat.type, Status.Done, ans))
        }
    }

    private fun doQueueOperation(flat: Flat<E>): E? {
        return when (flat.type) {
            OperationType.Poll -> {
                q.poll()
            }
            OperationType.Peek -> {
                q.peek()
            }
            OperationType.Add -> {
                q.add(flat.value)
                null
            }
        }
    }

    private fun tryLock(): Boolean {
        return lock.compareAndSet(expect = false, update = true)
    }

    private fun unlock() {
        lock.compareAndSet(expect = true, update = false)
    }
}

class Flat<E>(val type: OperationType, val status: Status = Status.Todo, val value: E? = null)

enum class OperationType {
    Poll,
    Peek,
    Add
}

enum class Status {
    Todo,
    Done
}