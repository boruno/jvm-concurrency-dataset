//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.Random
import kotlin.math.abs

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val randomizer = Random()

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val cell = abs(randomizer.nextInt() % ARRAY_SIZE)
        var cellValue = counters[cell].value
        counters[cell].compareAndSet(cellValue, cellValue + 1)
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