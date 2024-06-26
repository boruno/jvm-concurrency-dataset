package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        return
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return 1
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME