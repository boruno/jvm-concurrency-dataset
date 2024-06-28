package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
        counters[Random.nextInt() % ARRAY_SIZE].getAndAdd(1)
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var s: Int = 0
        s += counters[0].value
        return s
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME