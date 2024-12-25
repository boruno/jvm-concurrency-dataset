//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val index = Random.nextInt() % ARRAY_SIZE
        var curVal = counters[index].value
        while (!counters[index].compareAndSet(curVal, curVal + 1)) {
            curVal = counters[index].value
        }
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        (0 until ARRAY_SIZE).forEach { ind ->
            sum += counters[ind].value
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME