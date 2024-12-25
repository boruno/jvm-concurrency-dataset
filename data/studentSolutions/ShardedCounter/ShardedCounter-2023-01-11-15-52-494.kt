//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val idx = ThreadLocalRandom.current().nextInt(ARRAY_SIZE)
        val currValue = counters[idx].value
        counters[idx].value = currValue + 1
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var currSum = 0
        for ( i in 0 until ARRAY_SIZE) {
            currSum += counters[i].value
        }
        return currSum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME