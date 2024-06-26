package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val i = (0 until ARRAY_SIZE).random()
        counters[i].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var s = 0
        for(i in 0 until ARRAY_SIZE)
            s += counters[i].value
        return s
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME