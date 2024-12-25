//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class ShardedCounter {
    private val counters = Array(ARRAY_SIZE) {
        AtomicInteger(0)
    }

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {

        val index = Random.nextInt(ARRAY_SIZE)
        counters[index].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var result = 0
        for (i in 0..ARRAY_SIZE) {
            result += counters[i].get()
        }
        return result
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME