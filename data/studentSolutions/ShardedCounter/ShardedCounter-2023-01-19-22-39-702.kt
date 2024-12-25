//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.ThreadLocalRandom

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        counters[ThreadLocalRandom.current().nextInt(ARRAY_SIZE)].addAndGet(1)
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        for (i in 0..ARRAY_SIZE) {
            sum += counters[i].getAndAdd(0)
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME