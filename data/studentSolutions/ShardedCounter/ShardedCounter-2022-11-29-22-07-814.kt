package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlinx.atomicfu.locks.ReentrantLock
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)
    private val lock = ReentrantLock()

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val random = Random
        val index = random.nextInt(ARRAY_SIZE)
        counters[index].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var result = 0
        for (i in 0 until counters.size) {
            result += counters[i].value
        }
        return result
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME