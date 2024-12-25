//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        counters[Random.nextInt()].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var result = 0
        for(i in 0 until ARRAY_SIZE) {
            result += counters[i].value
        }
        return result
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME