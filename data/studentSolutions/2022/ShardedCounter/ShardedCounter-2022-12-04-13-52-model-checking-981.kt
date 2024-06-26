package mpp.counter

import kotlinx.atomicfu.AtomicIntArray

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        counters[(0 until ARRAY_SIZE).random()].value = 5;
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        TODO("implement me!")
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME