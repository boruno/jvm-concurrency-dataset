//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        // use Random.nextInt() % ARRAY_SIZE to choose the cell
        counters[index].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var answer = 0
        for (i in 0 until (ARRAY_SIZE - 1)) {
            answer += counters[i].value
        }
        return answer
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME