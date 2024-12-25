//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
        counters[Random.nextInt() % ARRAY_SIZE].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var s = 0;
        for(i in 0 .. ARRAY_SIZE) {
            s += counters[i].value
        }
        return s
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME