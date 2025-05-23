//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val rndIdX = (0..10).random() % ARRAY_SIZE
        counters[rndIdX] += 1
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum : Int = 0
        for (i in 0 until counters.size) {
            sum += counters[i].value
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME