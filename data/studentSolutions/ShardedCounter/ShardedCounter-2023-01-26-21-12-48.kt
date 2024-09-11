package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val cnt = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        cnt[Random.nextInt() % ARRAY_SIZE].addAndGet(1)
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        return cnt[0].value + cnt[1].value
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME