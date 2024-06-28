package mpp.counter

import kotlinx.atomicfu.AtomicIntArray

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index = (0 until ARRAY_SIZE).random()
        counters[index].value = counters[index].value + 1;
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return counters[0].value + counters[1].value
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME