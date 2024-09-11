package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import java.util.*

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
         counters[Random().nextInt(2) -1].getAndIncrement()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var sum = 0
        for (i in 0..1) {
            sum.plus(counters[i].value)
        }
        return sum
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME