//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val random = Random(49)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        counters[random.nextInt() % ARRAY_SIZE].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var x = 0
        for (i in 0 until ARRAY_SIZE) {
            x += counters[i].value
        }
        return x
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME
