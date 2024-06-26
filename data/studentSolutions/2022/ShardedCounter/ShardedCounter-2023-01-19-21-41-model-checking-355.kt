package mpp.counter

import kotlinx.atomicfu.AtomicIntArray

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    var x = 0

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        x++
        TODO("implement me!")
        // TODO: use Random.nextInt() % ARRAY_SIZE to choose the cell
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return x
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME