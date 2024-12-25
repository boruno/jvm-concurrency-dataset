//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val randomizer = Random()

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val i = randomizer.nextInt() % ARRAY_SIZE
        counters[i].value = counters[i].value + 1
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var counterResult = 0
        for (i in 0 until counters.size) {
            counterResult += counters[i].value
        }
        return counterResult
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME