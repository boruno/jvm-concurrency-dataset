//package mpp.counter

import kotlinx.atomicfu.AtomicIntArray
import kotlin.random.Random

class ShardedCounter {
    private val counters = AtomicIntArray(ARRAY_SIZE)

    /**
     * Atomically increments by one the current value of the counter.
     */
    fun inc() {
        counters[ Random.nextInt() % ARRAY_SIZE ].incrementAndGet()
    }

    /**
     * Returns the current counter value.
     */
    fun get(): Int {
        var answer = 0
        for ( i in 0..ARRAY_SIZE ) {
            answer += counters[i].value
        }
        return answer
    }
}

private const val ARRAY_SIZE = 2 // DO NOT CHANGE ME