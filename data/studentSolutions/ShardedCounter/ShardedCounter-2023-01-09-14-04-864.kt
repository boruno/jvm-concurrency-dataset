//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private var random = Random(222)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val selected = random.nextInt(0, ARRAY_SIZE)
        counters[selected].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var max = 0
        for (counter in 0 until ARRAY_SIZE) {
            if (counters[counter].value > max) {
                max = counters[counter].value
            }
        }
        return max
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME