//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        val ind = Random.nextInt() % ARRAY_SIZE
        counters[ind].incrementAndGet()
        return
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var ans = 0
        for(i in 0..ARRAY_SIZE) {
            ans += counters[i].value
        }
        return ans
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME