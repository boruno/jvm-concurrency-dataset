package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val random = Random(3564675)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        counters[random.nextInt() % ARRAY_SIZE].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        for (i in 0 until ARRAY_SIZE) {
            sum += counters[i].getAndAdd(0)
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME