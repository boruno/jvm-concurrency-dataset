//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
    * Atomically increments by one the current value of the counter.
    */
    fun inc() {
        val rnd = Random
        counters[rnd.nextInt() % ARRAY_SIZE].getAndIncrement()
    }

    /**
    * Returns the current counter value.
    */
    fun get(): Int {
        var res = 0
        var i = 0
        while (i < counters.size) {
            res += counters[i].value
            i++
        }
        return res
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME